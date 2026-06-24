import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { auth } from "@/auth";
import { SidebarNavigation } from "@/components/SidebarNavigation";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Risk Operations Command Center",
  description:
    "Real-time fraud decision monitoring for the Agentic Payment Integrity Engine.",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const session = await auth();
  const displayName =
    session?.user.preferredUsername ?? session?.user.name ?? null;
  const email = session?.user.email ?? null;

  return (
    <html lang="en" className="dark">
      <body
        className={`${geistSans.variable} ${geistMono.variable} min-h-screen bg-zinc-950 text-zinc-100 antialiased`}
      >
        <div className="min-h-screen bg-[radial-gradient(circle_at_top_left,rgba(16,185,129,0.14),transparent_34%),radial-gradient(circle_at_top_right,rgba(239,68,68,0.12),transparent_30%),linear-gradient(135deg,#020617_0%,#09090b_45%,#111827_100%)]">
          <SidebarNavigation displayName={displayName} email={email} />
          <main className="h-screen overflow-y-auto px-4 py-4 lg:ml-64 lg:px-6">
            {children}
          </main>
        </div>
      </body>
    </html>
  );
}
