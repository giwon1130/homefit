// Sentry SDK — 클라이언트(브라우저) 인스턴스. DSN 미설정이면 init 자체를 스킵 (no-op).
import * as Sentry from "@sentry/nextjs";

const dsn = process.env.NEXT_PUBLIC_SENTRY_DSN;

if (dsn) {
  Sentry.init({
    dsn,
    environment: process.env.NEXT_PUBLIC_SENTRY_ENV ?? "production",
    tracesSampleRate: 0.05,
    replaysSessionSampleRate: 0,        // 비활성 (PII 보호)
    replaysOnErrorSampleRate: 0.1,
  });
}
