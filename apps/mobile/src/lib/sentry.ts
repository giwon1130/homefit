import * as Sentry from "@sentry/react-native";
import Constants from "expo-constants";

/**
 * Sentry 초기화. RootLayout 에서 한 번 호출. DSN 미설정이면 init 자체를 스킵 (no-op).
 *
 * - DSN 은 app.json 의 extra.sentryDsn 또는 EXPO_PUBLIC_SENTRY_DSN 환경 변수.
 * - tracesSampleRate 0.05 — performance 샘플링 5%.
 * - sendDefaultPii=false — IP 등 자동 수집 차단.
 */
export function initSentry() {
  const dsn =
    (Constants.expoConfig?.extra as { sentryDsn?: string } | undefined)?.sentryDsn ??
    process.env.EXPO_PUBLIC_SENTRY_DSN;
  if (!dsn) return;

  Sentry.init({
    dsn,
    environment:
      (Constants.expoConfig?.extra as { sentryEnv?: string } | undefined)?.sentryEnv ??
      process.env.EXPO_PUBLIC_SENTRY_ENV ?? "production",
    tracesSampleRate: 0.05,
    sendDefaultPii: false,
    enableNativeFramesTracking: true,
  });
}

/** 로그인 후 호출 — Sentry 이벤트에 user.id 부착. */
export function setSentryUser(userId: number | string) {
  Sentry.setUser({ id: String(userId) });
}

/** 로그아웃 시. */
export function clearSentryUser() {
  Sentry.setUser(null);
}
