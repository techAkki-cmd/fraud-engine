"use client";

import { useEffect, useState } from "react";
import {
  CURRENT_SESSION_UPDATED_EVENT,
  getCurrentSessionPaymentIds,
} from "@/components/sessionWindow";
import type {
  FraudStatus,
  PaymentMethod,
  RiskPaymentEvent,
} from "@/components/types";

const MAX_LEDGER_ROWS = 100;
const DEFAULT_GATEWAY_STREAM_URL =
  "http://localhost:8080/api/v1/stream/payments";
const PAYMENT_METHODS: PaymentMethod[] = [
  "CARD",
  "BANK_TRANSFER",
  "DIGITAL_WALLET",
  "ACH",
  "UPI",
];

export type ConnectionState = "CONNECTING" | "ONLINE" | "OFFLINE";

type UsePaymentEventsOptions = {
  currentSessionOnly?: boolean;
};

export function usePaymentEvents({
  currentSessionOnly = true,
}: UsePaymentEventsOptions = {}) {
  const [events, setEvents] = useState<RiskPaymentEvent[]>([]);
  const [connectionState, setConnectionState] =
    useState<ConnectionState>("CONNECTING");
  const [allowedPaymentIds, setAllowedPaymentIds] = useState<Set<string>>(
    () => new Set(),
  );

  useEffect(() => {
    if (!currentSessionOnly) {
      return;
    }

    const refreshAllowedPaymentIds = () => {
      const paymentIds = getCurrentSessionPaymentIds();
      setAllowedPaymentIds(new Set(paymentIds));
      setEvents((currentEvents) =>
        currentEvents.filter((event) => paymentIds.includes(event.paymentId)),
      );
    };

    refreshAllowedPaymentIds();
    window.addEventListener(
      CURRENT_SESSION_UPDATED_EVENT,
      refreshAllowedPaymentIds,
    );
    window.addEventListener("storage", refreshAllowedPaymentIds);

    return () => {
      window.removeEventListener(
        CURRENT_SESSION_UPDATED_EVENT,
        refreshAllowedPaymentIds,
      );
      window.removeEventListener("storage", refreshAllowedPaymentIds);
    };
  }, [currentSessionOnly]);

  useEffect(() => {
    const streamUrl =
      process.env.NEXT_PUBLIC_GATEWAY_URL ?? DEFAULT_GATEWAY_STREAM_URL;
    const eventSource = new EventSource(streamUrl, { withCredentials: true });

    setConnectionState("CONNECTING");

    eventSource.onopen = () => {
      setConnectionState("ONLINE");
    };

    eventSource.onerror = () => {
      setConnectionState("OFFLINE");
    };

    eventSource.addEventListener("heartbeat", () => {
      if (eventSource.readyState === EventSource.OPEN) {
        setConnectionState("ONLINE");
      }
    });

    eventSource.addEventListener("payment-evaluation", (message) => {
      const event = parsePaymentEvent(message.data);
      if (event === null) {
        return;
      }

      if (currentSessionOnly && !allowedPaymentIds.has(event.paymentId)) {
        setConnectionState("ONLINE");
        return;
      }

      setEvents((currentEvents) =>
        [event, ...currentEvents].slice(0, MAX_LEDGER_ROWS),
      );
      setConnectionState("ONLINE");
    });

    return () => {
      eventSource.close();
    };
  }, [allowedPaymentIds, currentSessionOnly]);

  return { connectionState, events };
}

function parsePaymentEvent(rawData: string): RiskPaymentEvent | null {
  try {
    const value: unknown = JSON.parse(rawData);
    if (!isRecord(value)) {
      return null;
    }

    const event = {
      paymentId: stringField(value, "paymentId"),
      amount: stringField(value, "amount"),
      currency: stringField(value, "currency"),
      accountId: stringField(value, "accountId"),
      merchantId: stringField(value, "merchantId"),
      destinationAccountId: stringField(value, "destinationAccountId"),
      paymentMethod: paymentMethodField(value, "paymentMethod"),
      occurredAt: stringField(value, "occurredAt"),
      status: statusField(value, "status"),
      aiReasoning: stringField(value, "aiReasoning"),
      correlationId: stringField(value, "correlationId"),
      riskScore: optionalNumberField(value, "riskScore"),
      rulesTriggered: optionalStringField(value, "rulesTriggered"),
    };

    if (
      event.paymentId === null ||
      event.amount === null ||
      event.currency === null ||
      event.accountId === null ||
      event.merchantId === null ||
      event.destinationAccountId === null ||
      event.paymentMethod === null ||
      event.occurredAt === null ||
      event.status === null ||
      event.aiReasoning === null ||
      event.correlationId === null
    ) {
      return null;
    }

    return event as RiskPaymentEvent;
  } catch {
    return null;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function stringField(
  value: Record<string, unknown>,
  fieldName: string,
): string | null {
  const field = value[fieldName];
  return typeof field === "string" && field.trim().length > 0 ? field : null;
}

function paymentMethodField(
  value: Record<string, unknown>,
  fieldName: string,
): PaymentMethod | null {
  const field = value[fieldName];
  return typeof field === "string" &&
    PAYMENT_METHODS.includes(field as PaymentMethod)
    ? (field as PaymentMethod)
    : null;
}

function statusField(
  value: Record<string, unknown>,
  fieldName: string,
): FraudStatus | null {
  const field = value[fieldName];
  return field === "SAFE" || field === "REVIEW" || field === "BLOCK"
    ? field
    : null;
}

function optionalStringField(
  value: Record<string, unknown>,
  fieldName: string,
): string | undefined {
  const field = value[fieldName];
  return typeof field === "string" && field.trim().length > 0
    ? field
    : undefined;
}

function optionalNumberField(
  value: Record<string, unknown>,
  fieldName: string,
): number | undefined {
  const field = value[fieldName];
  return typeof field === "number" && Number.isFinite(field) ? field : undefined;
}
