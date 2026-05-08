// Sentry SDK — Next.js 서버(노드) 인스턴스. server actions / route handlers 에서 발생한 예외 캡처.
import * as Sentry from "@sentry/nextjs";

const dsn = process.env.SENTRY_DSN;

if (dsn) {
  Sentry.init({
    dsn,
    environment: process.env.SENTRY_ENV ?? "production",
    tracesSampleRate: 0.05,
  });
}
