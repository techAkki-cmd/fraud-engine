"use client";

import { useMemo, useState } from "react";
import {
  AlertCircle,
  Braces,
  CheckCircle2,
  Loader2,
  Play,
  ShieldCheck,
  ShieldQuestion,
  Siren,
} from "lucide-react";
import { recordCurrentSessionPaymentId } from "@/components/sessionWindow";

const DEFAULT_INGEST_URL = "http://localhost:8080/api/v1/payments";
const STREAM_SUFFIX = "/api/v1/stream/payments";
const INGEST_SUFFIX = "/api/v1/payments";
const CONSOLE_API_KEY = "risk-ops-console";
const PAYMENT_TOKEN_URL = "/api/auth/payment-token";

type TemplateKind = "SAFE" | "REVIEW" | "BLOCK";

export function PaymentOperationsConsole() {
  const [payload, setPayload] = useState(() =>
    JSON.stringify(createTemplate("SAFE"), null, 2),
  );
  const [jsonError, setJsonError] = useState<string | null>(null);
  const [networkLog, setNetworkLog] = useState(
    "$ Ready. Load a template, edit the JSON, then submit a transaction.",
  );
  const [isSubmitting, setIsSubmitting] = useState(false);
  const ingestUrl = useMemo(resolveGatewayIngestUrl, []);

  const loadTemplate = (kind: TemplateKind) => {
    setPayload(JSON.stringify(createTemplate(kind), null, 2));
    setJsonError(null);
    setNetworkLog(`$ Loaded ${kind.toLowerCase()} transaction template.`);
  };

  const submitTransaction = async () => {
    setJsonError(null);

    let parsedPayload: unknown;
    try {
      parsedPayload = JSON.parse(payload);
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Payload is not valid JSON.";
      setJsonError(`Invalid JSON: ${message}`);
      setNetworkLog("$ Submission blocked: JSON parser rejected the payload.");
      return;
    }

    const correlationId = crypto.randomUUID();
    setIsSubmitting(true);
    setNetworkLog(
      [
        `$ POST ${PAYMENT_TOKEN_URL}`,
        "> Requesting signed-in analyst JWT...",
        `$ POST ${ingestUrl}`,
        `> X-API-Key: ${CONSOLE_API_KEY}`,
        `> X-Correlation-ID: ${correlationId}`,
        "> Authorization: Bearer <analyst-jwt>",
        "> Waiting for gateway acknowledgement...",
      ].join("\n"),
    );

    try {
      const accessToken = await requestPaymentToken();
      const response = await fetch(ingestUrl, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
          "X-API-Key": CONSOLE_API_KEY,
          "X-Correlation-ID": correlationId,
        },
        body: JSON.stringify(parsedPayload),
      });

      const responseText = await response.text();
      const formattedBody = formatResponseBody(responseText);
      if (response.ok) {
        const submittedPaymentId = paymentIdFromPayload(parsedPayload);
        if (submittedPaymentId) {
          recordCurrentSessionPaymentId(submittedPaymentId);
        }
      }
      setNetworkLog(
        [
          `$ POST ${ingestUrl}`,
          `< HTTP ${response.status} ${response.statusText || "UNKNOWN"}`,
          `< X-Correlation-ID: ${correlationId}`,
          "",
          formattedBody,
        ].join("\n"),
      );
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Network request failed.";
      setNetworkLog(
        [
          `$ POST ${ingestUrl}`,
          "< NETWORK ERROR",
          `< ${message}`,
          "",
          networkHint(message),
        ].join("\n"),
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <section className="overflow-hidden rounded-3xl border border-zinc-800 bg-zinc-950 shadow-2xl shadow-black/30">
      <div className="flex flex-col gap-3 border-b border-zinc-800 bg-zinc-950/95 px-5 py-4 xl:flex-row xl:items-center xl:justify-between">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.24em] text-cyan-300">
            <Braces size={15} />
            Payment Operations Console
          </div>
          <h2 className="mt-1 text-lg font-semibold text-white">
            Edit and submit real Gateway payment payloads
          </h2>
          <p className="mt-1 text-xs text-zinc-500">
            POST target: <span className="font-mono text-zinc-300">{ingestUrl}</span>
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            className="inline-flex items-center gap-2 rounded-full border border-emerald-300/25 bg-emerald-400/10 px-3 py-2 text-xs font-bold uppercase tracking-[0.16em] text-emerald-200 transition hover:border-emerald-200/60 hover:bg-emerald-400/15"
            onClick={() => loadTemplate("SAFE")}
            type="button"
          >
            <ShieldCheck size={14} />
            Safe Payment
          </button>
          <button
            className="inline-flex items-center gap-2 rounded-full border border-amber-300/25 bg-amber-400/10 px-3 py-2 text-xs font-bold uppercase tracking-[0.16em] text-amber-100 transition hover:border-amber-200/60 hover:bg-amber-400/15"
            onClick={() => loadTemplate("REVIEW")}
            type="button"
          >
            <ShieldQuestion size={14} />
            Review Payment
          </button>
          <button
            className="inline-flex items-center gap-2 rounded-full border border-red-300/25 bg-red-500/10 px-3 py-2 text-xs font-bold uppercase tracking-[0.16em] text-red-200 transition hover:border-red-200/60 hover:bg-red-500/15"
            onClick={() => loadTemplate("BLOCK")}
            type="button"
          >
            <Siren size={14} />
            Block Payment
          </button>
        </div>
      </div>

      <div className="grid gap-4 p-5 xl:grid-cols-[minmax(0,1.35fr)_minmax(340px,0.65fr)]">
        <div>
          <label
            className="mb-2 block text-[11px] font-semibold uppercase tracking-[0.22em] text-zinc-500"
            htmlFor="payment-payload-editor"
          >
            Payment Request JSON
          </label>
          <textarea
            autoCapitalize="off"
            autoComplete="off"
            autoCorrect="off"
            className="min-h-[360px] w-full resize-y rounded-2xl border border-zinc-800 bg-black/60 p-4 font-mono text-sm leading-6 text-zinc-100 outline-none ring-0 transition placeholder:text-zinc-700 focus:border-cyan-300/50 focus:shadow-[0_0_0_1px_rgba(103,232,249,0.22)]"
            id="payment-payload-editor"
            onChange={(event) => {
              setPayload(event.target.value);
              if (jsonError) {
                setJsonError(null);
              }
            }}
            spellCheck={false}
            value={payload}
          />
          {jsonError ? (
            <div className="mt-3 flex items-start gap-2 rounded-2xl border border-red-400/20 bg-red-500/10 px-3 py-2 text-sm text-red-200">
              <AlertCircle className="mt-0.5 shrink-0" size={16} />
              <span>{jsonError}</span>
            </div>
          ) : (
            <div className="mt-3 flex items-center gap-2 text-xs text-zinc-500">
              <CheckCircle2 size={14} />
              Local JSON syntax is checked before the request leaves the browser.
            </div>
          )}
        </div>

        <div className="flex flex-col">
          <button
            className="inline-flex items-center justify-center gap-2 rounded-2xl border border-cyan-300/25 bg-cyan-400/10 px-4 py-3 text-sm font-bold uppercase tracking-[0.18em] text-cyan-100 transition hover:border-cyan-200/70 hover:bg-cyan-400/20 disabled:cursor-not-allowed disabled:opacity-50"
            disabled={isSubmitting}
            onClick={submitTransaction}
            type="button"
          >
            {isSubmitting ? (
              <Loader2 className="animate-spin" size={17} />
            ) : (
              <Play size={17} />
            )}
            {isSubmitting ? "Submitting" : "Submit Transaction"}
          </button>

          <div className="mt-4 flex-1 rounded-2xl border border-zinc-800 bg-black p-4">
            <div className="mb-3 flex items-center justify-between border-b border-zinc-900 pb-2">
              <span className="text-[11px] font-bold uppercase tracking-[0.22em] text-emerald-300">
                Network Log
              </span>
              <span className="rounded-full border border-zinc-800 px-2 py-0.5 font-mono text-[10px] text-zinc-500">
                gateway
              </span>
            </div>
            <pre className="min-h-[286px] whitespace-pre-wrap break-words font-mono text-xs leading-5 text-emerald-200/90">
              {networkLog}
            </pre>
          </div>
        </div>
      </div>
    </section>
  );
}

function paymentIdFromPayload(payload: unknown) {
  if (typeof payload !== "object" || payload === null || Array.isArray(payload)) {
    return null;
  }

  const record = payload as Record<string, unknown>;
  return typeof record.paymentId === "string" ? record.paymentId : null;
}

async function requestPaymentToken() {
  const response = await fetch(PAYMENT_TOKEN_URL, {
    method: "POST",
    cache: "no-store",
  });
  if (!response.ok) {
    const text = await response.text();
    if (response.status === 401) {
      throw new Error("Please sign in before submitting a payment.");
    }
    throw new Error(
      `Payment JWT request failed with HTTP ${response.status}: ${text || response.statusText}`,
    );
  }
  const body = (await response.json()) as { accessToken?: string };
  if (!body.accessToken) {
    throw new Error("Payment token response did not include accessToken.");
  }
  return body.accessToken;
}

function networkHint(message: string) {
  const lowered = message.toLowerCase();
  if (lowered.includes("failed to fetch") || lowered.includes("network")) {
    return "Check that edge-gateway is running, Keycloak is healthy, and Gateway CORS preflight allows the dashboard origin.";
  }
  if (lowered.includes("please sign in")) {
    return "Open the payment console sign-in flow, authenticate with Keycloak, then submit again.";
  }
  if (lowered.includes("401")) {
    return "The Gateway rejected the JWT. Check Keycloak realm import and Gateway issuer/JWK configuration.";
  }
  if (lowered.includes("403")) {
    return "The Gateway accepted authentication but denied authorization.";
  }
  return "Check the dashboard token route, Keycloak token endpoint, and edge-gateway logs.";
}

function createTemplate(kind: TemplateKind) {
  const now = new Date().toISOString();

  if (kind === "BLOCK") {
    return {
      paymentId: crypto.randomUUID(),
      accountId: "acct-bengaluru-vip-739201",
      destinationAccountId: "acct-navi-mumbai-mule-wallet-8842",
      merchantId: "merchant-mule-wallet-payout",
      amount: "95000.00",
      currency: "INR",
      paymentMethod: "DIGITAL_WALLET",
      occurredAt: now,
      riskContext: {
        amountToMedianRatio: 7.25,
        paymentAttemptsLast10Minutes: 8,
        ipGeoDistanceKmFromUsual: 3480,
        destinationAccountAgeDays: 1,
        destinationPreviouslyFlagged: true,
        deviceFingerprintRisk: "HIGH",
        merchantEstablishedLowRisk: false,
      },
    };
  }

  if (kind === "REVIEW") {
    return {
      paymentId: crypto.randomUUID(),
      accountId: "acct-pune-retail-662810",
      destinationAccountId: "acct-jaipur-marketplace-7781",
      merchantId: "merchant-jaipur-marketplace-seller",
      amount: "8750.40",
      currency: "INR",
      paymentMethod: "CARD",
      occurredAt: now,
      riskContext: {
        amountToMedianRatio: 6.1,
        paymentAttemptsLast10Minutes: 2,
        ipGeoDistanceKmFromUsual: 1325,
        destinationAccountAgeDays: 92,
        destinationPreviouslyFlagged: false,
        deviceFingerprintRisk: "MEDIUM",
        merchantEstablishedLowRisk: false,
      },
    };
  }

  return {
    paymentId: crypto.randomUUID(),
    accountId: "acct-mumbai-salary-104392",
    destinationAccountId: "acct-kirana-settlement-2048",
    merchantId: "merchant-andheri-kirana-store",
    amount: "450.00",
    currency: "INR",
    paymentMethod: "UPI",
    occurredAt: now,
    riskContext: {
      amountToMedianRatio: 1.05,
      paymentAttemptsLast10Minutes: 1,
      ipGeoDistanceKmFromUsual: 12,
      destinationAccountAgeDays: 720,
      destinationPreviouslyFlagged: false,
      deviceFingerprintRisk: "LOW",
      merchantEstablishedLowRisk: true,
    },
  };
}

function resolveGatewayIngestUrl() {
  const explicitUrl = process.env.NEXT_PUBLIC_GATEWAY_INGEST_URL;
  if (explicitUrl && explicitUrl.trim().length > 0) {
    return explicitUrl.trim();
  }

  const configuredUrl = process.env.NEXT_PUBLIC_GATEWAY_URL;
  if (!configuredUrl || configuredUrl.trim().length === 0) {
    return DEFAULT_INGEST_URL;
  }

  const trimmedUrl = configuredUrl.trim();
  if (trimmedUrl.endsWith(STREAM_SUFFIX)) {
    return `${trimmedUrl.slice(0, -STREAM_SUFFIX.length)}${INGEST_SUFFIX}`;
  }
  if (trimmedUrl.endsWith(INGEST_SUFFIX)) {
    return trimmedUrl;
  }

  try {
    return new URL(INGEST_SUFFIX, trimmedUrl).toString();
  } catch {
    return DEFAULT_INGEST_URL;
  }
}

function formatResponseBody(responseText: string) {
  if (responseText.trim().length === 0) {
    return "<empty response body>";
  }

  try {
    return JSON.stringify(JSON.parse(responseText), null, 2);
  } catch {
    return responseText;
  }
}
