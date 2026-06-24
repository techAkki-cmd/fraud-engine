"use client";

import { useEffect, useMemo, useState } from "react";
import type { ComponentType } from "react";
import {
  Activity,
  Ban,
  BrainCircuit,
  Clock3,
  RadioTower,
  ShieldCheck,
} from "lucide-react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  type EventWindowMode,
  EventWindowModeToggle,
} from "@/components/EventWindowModeToggle";
import type { RiskPaymentEvent } from "@/components/types";
import { usePaymentEvents } from "@/components/usePaymentEvents";

interface ChartBucket {
  label: string;
  cleared: number;
  review: number;
  blocked: number;
}

export function OverviewDashboard() {
  const [eventWindowMode, setEventWindowMode] =
    useState<EventWindowMode>("current");
  const { events } = usePaymentEvents({
    currentSessionOnly: eventWindowMode === "current",
  });
  const [isChartMounted, setIsChartMounted] = useState(false);
  const [timelineNow, setTimelineNow] = useState(() => Date.now());

  useEffect(() => {
    setIsChartMounted(true);
  }, []);

  useEffect(() => {
    const interval = window.setInterval(() => {
      setTimelineNow(Date.now());
    }, 5_000);

    return () => window.clearInterval(interval);
  }, []);

  const kpis = useMemo(() => {
    const total = events.length;
    const reviewEvents = events.filter((event) => event.status === "REVIEW");
    const blockedEvents = events.filter((event) => event.status === "BLOCK");
    const blockedAmount = blockedEvents.reduce(
      (sum, event) => sum + Number(event.amount),
      0,
    );
    const actionRate =
      total === 0
        ? 0
        : ((reviewEvents.length + blockedEvents.length) / total) * 100;
    const simulatedLatency =
      126 + (reviewEvents.length + blockedEvents.length) * 3;

    return {
      total,
      reviewCount: reviewEvents.length,
      blockedAmount,
      actionRate,
      simulatedLatency,
    };
  }, [events]);

  const chartData = useMemo(
    () => buildChartBuckets(events, timelineNow),
    [events, timelineNow],
  );

  return (
    <div className="mx-auto flex max-w-[1500px] flex-col gap-5">
      <header className="glass-panel rounded-3xl px-5 py-4">
        <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
          <div>
            <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.26em] text-emerald-300">
              <RadioTower size={15} />
              Live Risk Operations
            </div>
            <h1 className="mt-2 text-2xl font-semibold tracking-tight text-white md:text-3xl">
              AI Payment Integrity Command Center
            </h1>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-zinc-400">
              Monitor Kafka ingestion throughput, hybrid risk decisions, and
              ledger-bound cleared payments from one dense analyst console.
            </p>
          </div>
          <div className="flex flex-col gap-3">
            <div className="flex flex-wrap gap-2 text-xs xl:justify-end">
              <Pill tone="green" label="payment-cleared live" />
              <Pill tone="amber" label="payment-review live" />
              <Pill tone="red" label="payment-blocked live" />
              <Pill tone="blue" label="gateway sse bridge" />
            </div>
            <EventWindowModeToggle
              mode={eventWindowMode}
              onModeChange={setEventWindowMode}
            />
          </div>
        </div>
      </header>

      <section className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
        <KpiCard
          icon={Activity}
          label="Transactions Processed"
          value={kpis.total.toLocaleString()}
          detail="Rolling 100-event analyst window"
          tone="emerald"
        />
        <KpiCard
          icon={Ban}
          label="Blocked Value"
          value={currency(kpis.blockedAmount)}
          detail={`${kpis.actionRate.toFixed(1)}% review/block rate`}
          tone="red"
        />
        <KpiCard
          icon={BrainCircuit}
          label="Review Queue"
          value={kpis.reviewCount.toLocaleString()}
          detail="Step-up/manual-review decisions"
          tone="amber"
        />
        <KpiCard
          icon={BrainCircuit}
          label="AI Explainability"
          value={`${kpis.simulatedLatency}ms`}
          detail="Estimated Gemini reasoning p95"
          tone="cyan"
        />
        <KpiCard
          icon={ShieldCheck}
          label="Cleared Stream"
          value={events
            .filter((event) => event.status === "SAFE")
            .length.toLocaleString()}
          detail="Routed toward transactional ledger"
          tone="violet"
        />
      </section>

      <section className="glass-panel rounded-3xl p-5">
        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-zinc-500">
              Throughput Monitor
            </p>
            <h2 className="mt-1 text-lg font-semibold text-white">
              Cleared vs Review vs Blocked volume over recent minutes
            </h2>
          </div>
          <div className="flex items-center gap-2 rounded-full border border-white/10 bg-black/20 px-3 py-1.5 text-xs text-zinc-400">
            <Clock3 size={14} />
            Local time · 20s buckets · live Gateway SSE
          </div>
        </div>

        <div className="h-[310px] min-h-[310px]">
          {isChartMounted ? (
            <ResponsiveContainer height="100%" minHeight={310} width="100%">
              <LineChart data={chartData} margin={{ left: -18, right: 18 }}>
                <CartesianGrid
                  stroke="rgba(255,255,255,0.08)"
                  strokeDasharray="3 3"
                  vertical={false}
                />
                <XAxis
                  axisLine={false}
                  dataKey="label"
                  tick={{ fill: "#a1a1aa", fontSize: 11 }}
                  tickLine={false}
                />
                <YAxis
                  allowDecimals={false}
                  axisLine={false}
                  tick={{ fill: "#a1a1aa", fontSize: 11 }}
                  tickLine={false}
                />
                <Tooltip
                  contentStyle={{
                    background: "rgba(9,9,11,0.94)",
                    border: "1px solid rgba(255,255,255,0.12)",
                    borderRadius: 16,
                    color: "#fafafa",
                  }}
                  labelStyle={{ color: "#d4d4d8" }}
                />
                <Line
                  dataKey="cleared"
                  dot={false}
                  name="Cleared / SAFE"
                  stroke="#34d399"
                  strokeWidth={3}
                  type="monotone"
                />
                <Line
                  dataKey="review"
                  dot={false}
                  name="Review / step-up"
                  stroke="#fbbf24"
                  strokeWidth={3}
                  type="monotone"
                />
                <Line
                  dataKey="blocked"
                  dot={false}
                  name="Blocked"
                  stroke="#f87171"
                  strokeWidth={3}
                  type="monotone"
                />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <div className="flex h-full items-center justify-center rounded-2xl border border-white/10 bg-black/20 text-xs uppercase tracking-[0.18em] text-zinc-500">
              Initializing live chart
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

function KpiCard({
  icon: Icon,
  label,
  value,
  detail,
  tone,
}: {
  icon: ComponentType<{ size?: number; className?: string }>;
  label: string;
  value: string;
  detail: string;
  tone: "emerald" | "red" | "cyan" | "violet" | "amber";
}) {
  const toneClasses = {
    emerald: "text-emerald-300 bg-emerald-400/10 ring-emerald-300/20",
    red: "text-red-300 bg-red-400/10 ring-red-300/20",
    cyan: "text-cyan-300 bg-cyan-400/10 ring-cyan-300/20",
    violet: "text-violet-300 bg-violet-400/10 ring-violet-300/20",
    amber: "text-amber-200 bg-amber-400/10 ring-amber-300/20",
  }[tone];

  return (
    <article className="glass-panel rounded-3xl p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-zinc-500">
            {label}
          </p>
          <p className="mt-3 font-mono text-2xl font-semibold text-white">
            {value}
          </p>
        </div>
        <div
          className={`flex size-10 items-center justify-center rounded-2xl ring-1 ${toneClasses}`}
        >
          <Icon size={19} />
        </div>
      </div>
      <p className="mt-3 text-xs text-zinc-400">{detail}</p>
    </article>
  );
}

function Pill({
  label,
  tone,
}: {
  label: string;
  tone: "green" | "red" | "blue" | "amber";
}) {
  const toneClass = {
    green: "border-emerald-300/20 bg-emerald-400/10 text-emerald-200",
    red: "border-red-300/20 bg-red-400/10 text-red-200",
    blue: "border-cyan-300/20 bg-cyan-400/10 text-cyan-200",
    amber: "border-amber-300/20 bg-amber-400/10 text-amber-100",
  }[tone];

  return (
    <span
      className={`rounded-full border px-3 py-1.5 font-semibold uppercase tracking-[0.16em] ${toneClass}`}
    >
      {label}
    </span>
  );
}

function buildChartBuckets(events: RiskPaymentEvent[], now: number): ChartBucket[] {
  const bucketSizeMs = 20_000;
  const bucketCount = 10;

  const buckets: ChartBucket[] = Array.from({ length: bucketCount }, (_, i) => {
    const bucketStart = now - (bucketCount - i - 1) * bucketSizeMs;

    return {
      label: new Intl.DateTimeFormat("en-IN", {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      }).format(new Date(bucketStart)),
      cleared: 0,
      review: 0,
      blocked: 0,
    };
  });

  for (const event of events) {
    const age = now - new Date(event.occurredAt).getTime();
    const index = bucketCount - 1 - Math.floor(age / bucketSizeMs);

    if (index >= 0 && index < bucketCount) {
      if (event.status === "SAFE") {
        buckets[index].cleared += 1;
      } else if (event.status === "REVIEW") {
        buckets[index].review += 1;
      } else {
        buckets[index].blocked += 1;
      }
    }
  }

  return buckets;
}

function currency(value: number) {
  return new Intl.NumberFormat("en-IN", {
    currency: "INR",
    maximumFractionDigits: 0,
    style: "currency",
  }).format(value);
}
