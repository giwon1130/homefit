// Sentry SDK — Next.js Edge runtime (middleware 등) 인스턴스.
import * as Sentry from "@sentry/nextjs";

const dsn = process.env.SENTRY_DSN;

if (dsn) {
  Sentry.init({
    dsn,
    environment: process.env.SENTRY_ENV ?? "production",
    tracesSampleRate: 0.02,
  });
}
