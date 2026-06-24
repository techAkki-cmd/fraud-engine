import { AuthStatusControl } from "@/components/AuthStatusControl";

type AuthRequiredPanelProps = {
  description: string;
  title: string;
};

export function AuthRequiredPanel({ description, title }: AuthRequiredPanelProps) {
  return (
    <section className="rounded-3xl border border-cyan-300/20 bg-zinc-950 p-8 shadow-2xl shadow-black/30">
      <p className="text-xs font-semibold uppercase tracking-[0.24em] text-cyan-300">
        Analyst Authentication Required
      </p>
      <h1 className="mt-3 text-2xl font-semibold tracking-tight text-white">
        {title}
      </h1>
      <p className="mt-3 max-w-2xl text-sm leading-6 text-zinc-400">
        {description}
      </p>
      <div className="mt-6 max-w-xs">
        <AuthStatusControl compact />
      </div>
    </section>
  );
}
