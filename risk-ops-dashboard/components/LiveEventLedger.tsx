"use client";

import { useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  MousePointerClick,
  RadioTower,
  ShieldQuestion,
  WifiOff,
} from "lucide-react";
import { AIReasoningModal } from "@/components/AIReasoningModal";
import {
  type EventWindowMode,
  EventWindowModeToggle,
} from "@/components/EventWindowModeToggle";
import type { FraudStatus, RiskPaymentEvent } from "@/components/types";
import {
  type ConnectionState,
  usePaymentEvents,
} from "@/components/usePaymentEvents";

interface LiveEventLedgerProps {
  events?: RiskPaymentEvent[];
  connectionState?: ConnectionState;
  onInspectFraud?: (event: RiskPaymentEvent) => void;
  onSnapshot?: (events: RiskPaymentEvent[]) => void;
}

export function LiveEventLedger({
  events: providedEvents,
  connectionState: providedConnectionState,
  onInspectFraud,
  onSnapshot,
}: LiveEventLedgerProps) {
  const [eventWindowMode, setEventWindowMode] =
    useState<EventWindowMode>("current");
  const liveStream = usePaymentEvents({
    currentSessionOnly: eventWindowMode === "current",
  });
  const [selectedEvent, setSelectedEvent] = useState<RiskPaymentEvent | null>(
    null,
  );
  const events = providedEvents ?? liveStream.events;
  const connectionState =
    providedConnectionState ?? liveStream.connectionState;

  useEffect(() => {
    onSnapshot?.(events);
  }, [events, onSnapshot]);

  const actionCount = useMemo(
    () => events.filter((event) => event.status !== "SAFE").length,
    [events],
  );

  return (
    <section className="glass-panel overflow-hidden rounded-3xl">
      <div className="flex flex-col gap-4 border-b border-white/10 px-5 py-4 xl:flex-row xl:items-start xl:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.24em] text-zinc-500">
            Real-Time Event Ledger
          </p>
          <h2 className="mt-1 text-lg font-semibold text-white">
            Payment risk decisions streaming from Gateway SSE
          </h2>
        </div>
        <div className="flex flex-col gap-3 xl:items-end">
          <ConnectionBadge state={connectionState} actionCount={actionCount} />
          <EventWindowModeToggle
            mode={eventWindowMode}
            onModeChange={setEventWindowMode}
          />
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[920px] table-fixed text-left text-sm">
          <thead className="bg-white/[0.025] text-[11px] uppercase tracking-[0.2em] text-zinc-500">
            <tr>
              <th className="w-[170px] px-5 py-3 font-semibold">Timestamp</th>
              <th className="w-[310px] px-5 py-3 font-semibold">Payment ID</th>
              <th className="w-[130px] px-5 py-3 font-semibold">Amount</th>
              <th className="w-[145px] px-5 py-3 font-semibold">Status</th>
              <th className="px-5 py-3 font-semibold">Context</th>
            </tr>
          </thead>
          <tbody>
            {events.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-5 py-12">
                  <div className="flex flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-white/10 bg-black/20 px-6 py-10 text-center">
                    <WifiOff className="text-zinc-500" size={28} />
                    <div>
                      <p className="text-sm font-semibold text-zinc-200">
                        Waiting for live Gateway stream
                      </p>
                      <p className="mt-1 text-xs text-zinc-500">
                        {eventWindowMode === "current"
                          ? "Submit a payment from the Payment Console; current-session decisions will appear here."
                          : "No retained Gateway replay events are available in the current stream buffer."}
                      </p>
                    </div>
                  </div>
                </td>
              </tr>
            ) : (
              events.map((event) => (
                <tr
                  key={`${event.paymentId}-${event.occurredAt}-${event.status}`}
                  className={`ledger-row transition ${
                    event.status !== "SAFE"
                      ? "cursor-pointer hover:bg-red-500/[0.045]"
                      : ""
                  }`}
                  onClick={() => {
                    if (event.status !== "SAFE") {
                      onInspectFraud?.(event);
                      if (!onInspectFraud) {
                        setSelectedEvent(event);
                      }
                    }
                  }}
                >
                  <td className="px-5 py-3 font-mono text-xs text-zinc-400">
                    {formatTimestamp(event.occurredAt)}
                  </td>
                  <td className="truncate px-5 py-3 font-mono text-xs text-zinc-200">
                    {event.paymentId}
                  </td>
                  <td className="px-5 py-3 font-mono text-xs font-semibold text-zinc-100">
                    {formatMoney(event.amount, event.currency)}
                  </td>
                  <td className="px-5 py-3">
                    <StatusBadge status={event.status} />
                  </td>
                  <td className="px-5 py-3">
                    <div className="flex items-center gap-2 text-xs text-zinc-400">
                      <span className="truncate">
                        {event.paymentMethod} · {event.accountId} →{" "}
                        {event.destinationAccountId}
                      </span>
                      {event.status !== "SAFE" ? (
                        <MousePointerClick
                          className="shrink-0 text-amber-300/80"
                          size={14}
                        />
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      <AIReasoningModal
        event={selectedEvent}
        onClose={() => setSelectedEvent(null)}
      />
    </section>
  );
}

function ConnectionBadge({
  state,
  actionCount,
}: {
  state: ConnectionState;
  actionCount: number;
}) {
  const styles = {
    CONNECTING: {
      dot: "bg-cyan-300 shadow-[0_0_16px_rgba(103,232,249,0.8)] animate-pulse",
      text: "Connecting to Gateway SSE",
    },
    ONLINE: {
      dot: "bg-emerald-400 shadow-[0_0_16px_rgba(52,211,153,0.9)]",
      text: `Online · ${actionCount} review/block rows in window`,
    },
    OFFLINE: {
      dot: "bg-red-400 shadow-[0_0_16px_rgba(248,113,113,0.9)] animate-pulse",
      text: "Offline · browser will retry automatically",
    },
  }[state];

  return (
    <div className="flex items-center gap-2 rounded-full border border-white/10 bg-white/[0.03] px-3 py-1.5 text-xs text-zinc-300">
      {state === "ONLINE" ? <RadioTower size={13} /> : null}
      <span className={`size-2 rounded-full ${styles.dot}`} />
      {styles.text}
    </div>
  );
}

function StatusBadge({ status }: { status: FraudStatus }) {
  if (status === "BLOCK") {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full border border-red-300/25 bg-red-500/10 px-2.5 py-1 text-[11px] font-bold uppercase tracking-[0.18em] text-red-200 fraud-glow">
        <AlertTriangle className="animate-pulse" size={13} />
        BLOCK
      </span>
    );
  }

  if (status === "REVIEW") {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full border border-amber-300/25 bg-amber-400/10 px-2.5 py-1 text-[11px] font-bold uppercase tracking-[0.18em] text-amber-200">
        <ShieldQuestion size={13} />
        REVIEW
      </span>
    );
  }

  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-300/20 bg-emerald-400/10 px-2.5 py-1 text-[11px] font-bold uppercase tracking-[0.18em] text-emerald-200">
      <CheckCircle2 size={13} />
      SAFE
    </span>
  );
}

function formatTimestamp(isoTimestamp: string) {
  return new Intl.DateTimeFormat("en-IN", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(new Date(isoTimestamp));
}

function formatMoney(amount: string, currency: string) {
  const numericAmount = Number(amount);
  if (!Number.isFinite(numericAmount)) {
    return `${currency} ${amount}`;
  }

  return new Intl.NumberFormat("en-IN", {
    currency,
    maximumFractionDigits: 2,
    minimumFractionDigits: 2,
    style: "currency",
  }).format(numericAmount);
}
