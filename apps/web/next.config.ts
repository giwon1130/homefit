import type { NextConfig } from "next";
import { withSentryConfig } from "@sentry/nextjs";

const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

// CSP 화이트리스트:
//  - script: self + Daum 우편번호 CDN
//  - style:  self + inline (Tailwind/styled-jsx)
//  - img:    self + data:URI + OSM 타일 + Leaflet 마커 + Google 프로필 사진
//  - connect: self + 우리 API + VWorld + ODsay
//  - frame:  Daum 팝업
const csp = [
  "default-src 'self'",
  "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://t1.daumcdn.net https://*.daumcdn.net",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob: https://tile.openstreetmap.org https://*.tile.openstreetmap.org https://unpkg.com https://*.googleusercontent.com",
  `connect-src 'self' ${apiBase} https://api.vworld.kr https://api.odsay.com https://nominatim.openstreetmap.org https://*.sentry.io https://*.ingest.sentry.io`,
  "font-src 'self' data:",
  "frame-src https://postcode.map.daum.net",
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "object-src 'none'",
].join("; ");

const securityHeaders = [
  { key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains; preload" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=(), usb=()" },
  { key: "Content-Security-Policy", value: csp },
];

const nextConfig: NextConfig = {
  reactStrictMode: true,
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

// Sentry 가 활성화된 환경(SENTRY_DSN/AUTH_TOKEN) 에서만 wrap 적용.
// 미설정이면 plain config 그대로 export → 빌드 영향 0.
const sentryWrapped = process.env.SENTRY_DSN
  ? withSentryConfig(nextConfig, {
      silent: true,
      org: process.env.SENTRY_ORG,
      project: process.env.SENTRY_PROJECT,
      authToken: process.env.SENTRY_AUTH_TOKEN,
      // sourcemap 업로드는 SENTRY_AUTH_TOKEN 있을 때만.
      widenClientFileUpload: true,
      sourcemaps: { disable: !process.env.SENTRY_AUTH_TOKEN },
      disableLogger: true,
    })
  : nextConfig;

export default sentryWrapped;
