import { auth } from "@/auth";
import { AuthRequiredPanel } from "@/components/AuthRequiredPanel";
import { PaymentOperationsConsole } from "@/components/PaymentOperationsConsole";

export default async function PaymentConsolePage() {
  const session = await auth();

  return (
    <div className="mx-auto flex max-w-[1500px] flex-col gap-5">
      {session ? (
        <PaymentOperationsConsole />
      ) : (
        <AuthRequiredPanel
          description="The payment console sends user-scoped Keycloak access tokens to the secured Gateway payment endpoint."
          title="Sign in before submitting payments"
        />
      )}
    </div>
  );
}
