import { auth } from "@/auth";
import { AuthRequiredPanel } from "@/components/AuthRequiredPanel";
import { OverviewDashboard } from "@/components/OverviewDashboard";

export default async function OverviewPage() {
  const session = await auth();

  if (!session) {
    return (
      <div className="mx-auto flex max-w-[1500px] flex-col gap-5">
        <AuthRequiredPanel
          description="Sign in with Keycloak before viewing payment metrics, throughput charts, and fraud operations telemetry."
          title="Sign in before viewing risk operations data"
        />
      </div>
    );
  }

  return <OverviewDashboard />;
}
