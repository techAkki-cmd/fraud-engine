import { getToken } from "next-auth/jwt";
import { type NextRequest, NextResponse } from "next/server";

const DEFAULT_LOGOUT_URL =
  "http://localhost:8089/realms/fraud-engine/protocol/openid-connect/logout";
const DEFAULT_POST_LOGOUT_REDIRECT_URL = "http://localhost:3001/";

const AUTH_COOKIE_NAMES = [
  "authjs.session-token",
  "__Secure-authjs.session-token",
  "authjs.callback-url",
  "__Secure-authjs.callback-url",
  "authjs.csrf-token",
  "__Host-authjs.csrf-token",
];

export async function GET(request: NextRequest) {
  const logoutUrl = new URL(
    process.env.DASHBOARD_OIDC_LOGOUT_URL ?? DEFAULT_LOGOUT_URL,
  );
  logoutUrl.searchParams.set(
    "post_logout_redirect_uri",
    process.env.DASHBOARD_POST_LOGOUT_REDIRECT_URL ??
      DEFAULT_POST_LOGOUT_REDIRECT_URL,
  );

  const token = await getToken({
    req: request,
    secret: process.env.AUTH_SECRET,
    secureCookie: false,
  });
  if (typeof token?.idToken === "string") {
    logoutUrl.searchParams.set("id_token_hint", token.idToken);
  }

  const response = NextResponse.redirect(logoutUrl);
  for (const cookieName of AUTH_COOKIE_NAMES) {
    response.cookies.delete(cookieName);
  }
  return response;
}
