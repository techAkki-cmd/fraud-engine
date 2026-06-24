"use client";

import { History, TimerReset } from "lucide-react";

export type EventWindowMode = "current" | "history";

type EventWindowModeToggleProps = {
  mode: EventWindowMode;
  onModeChange: (mode: EventWindowMode) => void;
};

export function EventWindowModeToggle({
  mode,
  onModeChange,
}: EventWindowModeToggleProps) {
  const helperText =
    mode === "current"
      ? "Current Session shows only transactions submitted from this browser."
      : "Event History shows retained Gateway replay events without deleting ledger history.";

  return (
    <div className="flex flex-col gap-2">
      <div
        aria-label="Event history window"
        className="grid grid-cols-2 rounded-2xl border border-white/10 bg-black/30 p-1"
        role="group"
      >
        <ModeButton
          active={mode === "current"}
          icon={TimerReset}
          label="Current Session"
          onClick={() => onModeChange("current")}
        />
        <ModeButton
          active={mode === "history"}
          icon={History}
          label="Event History"
          onClick={() => onModeChange("history")}
        />
      </div>
      <p className="max-w-md text-xs leading-5 text-zinc-500">{helperText}</p>
    </div>
  );
}

function ModeButton({
  active,
  icon: Icon,
  label,
  onClick,
}: {
  active: boolean;
  icon: typeof History;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      aria-pressed={active}
      className={`inline-flex min-h-10 items-center justify-center gap-2 rounded-xl px-3 text-xs font-bold uppercase tracking-[0.14em] transition ${
        active
          ? "bg-cyan-300/15 text-cyan-100 ring-1 ring-cyan-200/35"
          : "text-zinc-500 hover:bg-white/[0.04] hover:text-zinc-200"
      }`}
      onClick={onClick}
      type="button"
    >
      <Icon size={14} />
      {label}
    </button>
  );
}
