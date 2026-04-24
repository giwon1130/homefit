import NextAuth, { type DefaultSession } from "next-auth";
import Google from "next-auth/providers/google";

declare module "next-auth" {
  interface Session {
    accessToken?: string;
    refreshToken?: string;
    backendUserId?: number;
    user: {
      id?: number;
    } & DefaultSession["user"];
  }
}

const API = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [
    Google({
      clientId: process.env.GOOGLE_OAUTH_CLIENT_ID,
      clientSecret: process.env.GOOGLE_OAUTH_CLIENT_SECRET,
    }),
  ],
  callbacks: {
    async jwt({ token, account }) {
      // Initial sign-in: exchange Google id_token for our backend JWT.
      if (account?.id_token) {
        try {
          const res = await fetch(`${API}/api/v1/auth/google`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ idToken: account.id_token }),
          });
          if (res.ok) {
            const data = (await res.json()) as {
              accessToken: string;
              refreshToken: string;
              user?: { id: number };
            };
            (token as Record<string, unknown>).accessToken = data.accessToken;
            (token as Record<string, unknown>).refreshToken = data.refreshToken;
            (token as Record<string, unknown>).backendUserId = data.user?.id;
          } else {
            console.error(
              "backend auth exchange returned non-ok",
              res.status,
              await res.text(),
            );
          }
        } catch (e) {
          console.error("backend auth exchange failed", e);
        }
      }
      return token;
    },
    async session({ session, token }) {
      const t = token as Record<string, unknown>;
      session.accessToken = t.accessToken as string | undefined;
      session.refreshToken = t.refreshToken as string | undefined;
      session.backendUserId = t.backendUserId as number | undefined;
      if (session.user && typeof t.backendUserId === "number") {
        (session.user as { id?: number }).id = t.backendUserId;
      }
      return session;
    },
  },
  pages: {
    signIn: "/login",
  },
});
