import { type DefaultSession } from "next-auth";
import { type JWT as DefaultJWT } from "next-auth/jwt";

declare module "next-auth" {
  interface Session {
    accessToken?: string;
    accessTokenExpiresAt?: number;
    user: DefaultSession["user"] & {
      preferredUsername?: string;
    };
  }
}

declare module "next-auth/jwt" {
  interface JWT extends DefaultJWT {
    accessToken?: string;
    accessTokenExpiresAt?: number;
    idToken?: string;
    preferredUsername?: string;
  }
}
