"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  ListOrdered,
  ShieldAlert,
  TerminalSquare,
} from "lucide-react";
import { AuthStatusControl } from "@/components/AuthStatusControl";

const navItems = [
  { label: "Overview", href: "/", icon: LayoutDashboard },
  { label: "Live Ledger", href: "/transactions", icon: ListOrdered },
  { label: "Payment Console", href: "/payment-console", icon: TerminalSquare },
];

type SidebarNavigationProps = {
  displayName?: string | null;
  email?: string | null;
};

export function SidebarNavigation({ displayName, email }: SidebarNavigationProps) {
  const pathname = usePathname();

  return (
    <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 border-r border-white/10 bg-zinc-950/80 px-4 py-5 shadow-2xl shadow-black/40 backdrop-blur-xl lg:block">
      <div className="flex items-center gap-3 rounded-2xl border border-emerald-400/20 bg-emerald-400/5 p-3">
        <div className="flex size-10 items-center justify-center rounded-xl bg-emerald-400/15 text-emerald-300 ring-1 ring-emerald-300/30">
          <ShieldAlert size={21} />
        </div>
        <div>
          <p className="text-sm font-semibold tracking-wide text-white">
            Fraud Engine
          </p>
          <p className="text-[11px] uppercase tracking-[0.24em] text-emerald-300/80">
            Risk Ops
          </p>
        </div>
      </div>

      <nav className="mt-8 space-y-1.5" aria-label="Risk operations pages">
        {navItems.map((item) => {
          const Icon = item.icon;
          const active =
            item.href === "/"
              ? pathname === "/"
              : pathname === item.href || pathname.startsWith(`${item.href}/`);

          return (
            <Link
              key={item.href}
              href={item.href}
              className={`group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition ${
                active
                  ? "border border-emerald-400/25 bg-zinc-800 text-emerald-300 shadow-[0_0_24px_rgba(16,185,129,0.12)]"
                  : "text-zinc-400 hover:bg-white/[0.04] hover:text-zinc-100"
              }`}
            >
              <Icon
                size={17}
                className={
                  active
                    ? "text-emerald-300"
                    : "text-zinc-500 group-hover:text-zinc-200"
                }
              />
              <span>{item.label}</span>
            </Link>
          );
        })}
      </nav>

      <div className="absolute bottom-5 left-4 right-4 space-y-3 rounded-2xl border border-white/10 bg-white/[0.03] p-4">
        <AuthStatusControl displayName={displayName} email={email} />
        <div className="border-t border-white/10 pt-3">
        <p className="text-xs font-semibold uppercase tracking-[0.22em] text-zinc-500">
          Stream Health
        </p>
        <div className="mt-3 flex items-center gap-2 text-sm text-emerald-300">
          <span className="size-2 rounded-full bg-emerald-400 shadow-[0_0_18px_rgba(52,211,153,0.8)]" />
          Kafka / AI / Ledger online
        </div>
        </div>
      </div>
    </aside>
  );
}
