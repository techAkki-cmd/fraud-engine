"use client";

import { useEffect } from "react";
import {
  ArrowRight,
  BrainCircuit,
  ShieldAlert,
  ShieldQuestion,
  X,
} from "lucide-react";
import type { RiskPaymentEvent } from "@/components/types";

interface AIReasoningModalProps {
  event: RiskPaymentEvent | null;
  onClose: () => void;
}

export function AIReasoningModal({ event, onClose }: AIReasoningModalProps) {
  useEffect(() => {
    if (!event) {
      return;
    }

    const handleKeyDown = (keyboardEvent: KeyboardEvent) => {
      if (keyboardEvent.key === "Escape") {
        onClose();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [event, onClose]);

  if (!event) {
    return null;
  }

  const isBlock = event.status === "BLOCK";
  const statusTone = isBlock
    ? "border-red-300/25 bg-red-500/10 text-red-200"
    : "border-amber-300/25 bg-amber-400/10 text-amber-100";
  const accentTone = isBlock ? "text-red-300" : "text-amber-200";
  const iconTone = isBlock
    ? "bg-red-500/15 text-red-300 ring-red-300/25"
    : "bg-amber-400/15 text-amber-200 ring-amber-300/25";
  const barTone = isBlock ? "bg-red-300" : "bg-amber-300";
  const riskScore = event.riskScore ?? 0;

  return (
    <div
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/75 px-4 pb-6 pt-6 backdrop-blur-md sm:pt-10"
      role="dialog"
      onMouseDown={onClose}
    >
      <section
        className="glass-panel flex max-h-[min(680px,calc(100vh-5rem))] w-full max-w-[640px] flex-col overflow-hidden rounded-3xl"
        onMouseDown={(mouseEvent) => mouseEvent.stopPropagation()}
      >
        <header className="flex shrink-0 flex-col gap-4 border-b border-white/10 p-5 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex min-w-0 items-center gap-3">
            <div
              className={`flex size-11 shrink-0 items-center justify-center rounded-2xl ring-1 ${iconTone}`}
            >
              {isBlock ? (
                <ShieldAlert size={22} />
              ) : (
                <ShieldQuestion size={22} />
              )}
            </div>
            <div className="min-w-0">
              <p
                className={`text-[11px] font-semibold uppercase tracking-[0.22em] ${accentTone}`}
              >
                Hybrid Risk Engine Decision
              </p>
              <h2 className="mt-1 text-xl font-semibold tracking-tight text-white sm:text-2xl">
                Transaction {isBlock ? "Blocked" : "Requires Review"}
              </h2>
            </div>
          </div>

          <div className="flex shrink-0 items-center gap-2 self-start">
            <span
              className={`inline-flex items-center rounded-full border px-3 py-1.5 text-xs font-bold uppercase tracking-[0.16em] ${statusTone}`}
            >
              {event.status}
            </span>
            <button
              aria-label="Close risk decision inspector"
              className="rounded-full border border-white/10 p-2 text-zinc-400 transition hover:border-white/20 hover:bg-white/10 hover:text-white"
              type="button"
              onClick={onClose}
            >
              <X size={18} />
            </button>
          </div>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto">
          <div className="space-y-3 p-5">
            <div className="grid gap-3 sm:grid-cols-4">
              <Metric
                label="Amount"
                value={formatMoney(event.amount, event.currency)}
              />
              <Metric
                label="Method"
                value={event.paymentMethod.replace("_", " ")}
              />
              <Metric
                accentClass={barTone}
                label="Risk"
                value={event.riskScore !== undefined ? `${event.riskScore}/100` : "N/A"}
                widthPercent={riskScore}
              />
              <Metric label="Time" value={formatTimestamp(event.occurredAt)} />
            </div>

            <div className="rounded-2xl border border-white/10 bg-black/20 p-4">
              <div className="mb-3 flex items-center justify-between gap-3">
                <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-zinc-500">
                  Account Route
                </p>
                <span
                  className="truncate text-right font-mono text-[11px] text-zinc-500"
                  title={event.merchantId}
                >
                  {event.merchantId}
                </span>
              </div>
              <div className="grid min-w-0 items-center gap-3 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)]">
                <AccountPill label="Source" value={event.accountId} />
                <ArrowRight
                  className="mx-auto hidden text-zinc-500 sm:block"
                  size={18}
                />
                <AccountPill
                  label="Destination"
                  value={event.destinationAccountId}
                />
              </div>
            </div>

            <div className="rounded-2xl border border-white/10 bg-black/20 p-4">
              <div
                className={`mb-2 flex items-center gap-2 text-sm font-semibold ${accentTone}`}
              >
                <BrainCircuit size={17} />
                Analyst Reasoning
              </div>
              <p className="max-h-32 overflow-y-auto break-words pr-1 text-sm leading-6 text-zinc-200">
                {event.aiReasoning}
              </p>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}

function Metric({
  accentClass,
  label,
  value,
  widthPercent,
}: {
  accentClass?: string;
  label: string;
  value: string;
  widthPercent?: number;
}) {
  return (
    <div className="min-w-0 rounded-2xl border border-white/10 bg-black/20 px-3 py-3">
      <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-zinc-500">
        {label}
      </p>
      <p className="mt-1 truncate text-sm font-semibold text-zinc-100" title={value}>
        {value}
      </p>
      {widthPercent !== undefined ? (
        <div className="mt-2 h-1 overflow-hidden rounded-full bg-white/10">
          <div
            className={`h-full rounded-full ${accentClass ?? "bg-zinc-300"}`}
            style={{ width: `${Math.max(0, Math.min(100, widthPercent))}%` }}
          />
        </div>
      ) : null}
    </div>
  );
}

function AccountPill({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-xl border border-white/10 bg-white/[0.025] px-3 py-2.5">
      <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-zinc-500">
        {label}
      </p>
      <p className="mt-1 truncate font-mono text-xs leading-5 text-zinc-100" title={value}>
        {value}
      </p>
    </div>
  );
}

function formatTimestamp(isoTimestamp: string) {
  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    month: "short",
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
