import NextAuth, { type NextAuthConfig } from "next-auth";

const DEFAULT_ISSUER = "http://localhost:8089/realms/fraud-engine";
const DEFAULT_AUTHORIZATION_URL =
  "http://localhost:8089/realms/fraud-engine/protocol/openid-connect/auth";
const DEFAULT_TOKEN_URL =
  "http://keycloak:8080/realms/fraud-engine/protocol/openid-connect/token";
const DEFAULT_USERINFO_URL =
  "http://keycloak:8080/realms/fraud-engine/protocol/openid-connect/userinfo";
const DEFAULT_JWKS_URL =
  "http://keycloak:8080/realms/fraud-engine/protocol/openid-connect/certs";
const DEFAULT_CLIENT_ID = "risk-ops-dashboard";
const DEFAULT_CLIENT_SECRET = "local-dashboard-client-secret";

export const authConfig = {
  trustHost: true,
  session: {
    strategy: "jwt",
  },
  providers: [
    {
      id: "keycloak",
      name: "Keycloak",
      type: "oidc",
      issuer: process.env.DASHBOARD_OIDC_ISSUER ?? DEFAULT_ISSUER,
      clientId: process.env.DASHBOARD_OIDC_CLIENT_ID ?? DEFAULT_CLIENT_ID,
      clientSecret:
        process.env.DASHBOARD_OIDC_CLIENT_SECRET ?? DEFAULT_CLIENT_SECRET,
      authorization: {
        url:
          process.env.DASHBOARD_OIDC_AUTHORIZATION_URL ??
          DEFAULT_AUTHORIZATION_URL,
        params: {
          scope: "openid profile email",
        },
      },
      token: process.env.DASHBOARD_OIDC_TOKEN_URL ?? DEFAULT_TOKEN_URL,
      userinfo: process.env.DASHBOARD_OIDC_USERINFO_URL ?? DEFAULT_USERINFO_URL,
      jwks_endpoint: process.env.DASHBOARD_OIDC_JWKS_URL ?? DEFAULT_JWKS_URL,
      checks: ["pkce", "state"],
      profile(profile) {
        return {
          id: profile.sub,
          name:
            profile.name ??
            profile.preferred_username ??
            "Risk Operations Analyst",
          email: profile.email,
        };
      },
    },
  ],
  callbacks: {
    jwt({ token, account, profile }) {
      if (account?.access_token) {
        token.accessToken = account.access_token;
        token.accessTokenExpiresAt =
          typeof account.expires_at === "number"
            ? account.expires_at * 1000
            : Date.now() + 300_000;
      }
      if (account?.id_token) {
        token.idToken = account.id_token;
      }
      if (profile?.preferred_username) {
        token.preferredUsername = profile.preferred_username;
      }
      return token;
    },
    session({ session, token }) {
      session.accessToken =
        typeof token.accessToken === "string" ? token.accessToken : undefined;
      session.accessTokenExpiresAt =
        typeof token.accessTokenExpiresAt === "number"
          ? token.accessTokenExpiresAt
          : undefined;
      session.user.preferredUsername =
        typeof token.preferredUsername === "string"
          ? token.preferredUsername
          : undefined;
      return session;
    },
  },
} satisfies NextAuthConfig;

export const { handlers, auth, signIn, signOut } = NextAuth(authConfig);
