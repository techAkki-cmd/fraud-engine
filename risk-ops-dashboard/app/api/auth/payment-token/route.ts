import { auth } from "@/auth";
import { NextResponse } from "next/server";

export async function POST() {
  const session = await auth();
  const accessToken = session?.accessToken;

  if (!accessToken) {
    return NextResponse.json(
      { error: "Please sign in to submit authenticated payment requests." },
      { status: 401 },
    );
  }

  if (
    session.accessTokenExpiresAt &&
    session.accessTokenExpiresAt <= Date.now()
  ) {
    return NextResponse.json(
      { error: "Your dashboard session has expired. Please sign in again." },
      { status: 401 },
    );
  }

  return NextResponse.json({
    accessToken,
    expiresAt: session.accessTokenExpiresAt,
    tokenType: "Bearer",
  });
}
