"use client";

import { LogIn, LogOut, UserRound } from "lucide-react";
import { useState } from "react";
import { signIn } from "next-auth/react";
import { clearCurrentSessionPaymentIds } from "@/components/sessionWindow";

type AuthStatusControlProps = {
  displayName?: string | null;
  email?: string | null;
  compact?: boolean;
};

export function AuthStatusControl({
  displayName,
  email,
  compact = false,
}: AuthStatusControlProps) {
  const [isSigningOut, setIsSigningOut] = useState(false);
  const signedIn = Boolean(displayName || email);
  const label = displayName ?? email ?? "Not signed in";

  if (!signedIn) {
    return (
      <button
        className="inline-flex w-full items-center justify-center gap-2 rounded-xl border border-cyan-300/25 bg-cyan-400/10 px-3 py-2 text-xs font-bold uppercase tracking-[0.16em] text-cyan-100 transition hover:border-cyan-200/70 hover:bg-cyan-400/20"
        onClick={() => {
          clearCurrentSessionPaymentIds();
          signIn("keycloak", { callbackUrl: "/payment-console" });
        }}
        type="button"
      >
        <LogIn size={14} />
        Sign in
      </button>
    );
  }

  return (
    <div className="space-y-2">
      {!compact ? (
        <div className="flex items-center gap-2 text-sm text-zinc-200">
          <UserRound className="shrink-0 text-emerald-300" size={16} />
          <span className="truncate">{label}</span>
        </div>
      ) : null}
      <button
        className="inline-flex w-full items-center justify-center gap-2 rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs font-bold uppercase tracking-[0.16em] text-zinc-200 transition hover:border-zinc-500 hover:bg-zinc-800"
        disabled={isSigningOut}
        onClick={() => {
          setIsSigningOut(true);
          clearCurrentSessionPaymentIds();
          window.location.assign("/api/auth/keycloak-logout");
        }}
        type="button"
      >
        <LogOut size={14} />
        {isSigningOut ? "Signing out" : "Sign out"}
      </button>
    </div>
  );
}
