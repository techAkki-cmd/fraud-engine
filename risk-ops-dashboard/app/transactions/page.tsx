import { auth } from "@/auth";
import { AuthRequiredPanel } from "@/components/AuthRequiredPanel";
import { LiveEventLedger } from "@/components/LiveEventLedger";

export default async function TransactionsPage() {
  const session = await auth();

  return (
    <div className="mx-auto flex max-w-[1500px] flex-col gap-5">
      {session ? (
        <LiveEventLedger />
      ) : (
        <AuthRequiredPanel
          description="Sign in with Keycloak before viewing live payment IDs, account routing details, fraud decisions, and analyst context."
          title="Sign in before viewing the live ledger"
        />
      )}
    </div>
  );
}
