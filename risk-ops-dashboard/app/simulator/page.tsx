import { redirect } from "next/navigation";

export default async function LegacyConsoleRedirectPage() {
  redirect("/payment-console");
}
