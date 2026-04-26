import type { NextConfig } from "next";

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
  `connect-src 'self' ${apiBase} https://api.vworld.kr https://api.odsay.com https://nominatim.openstreetmap.org`,
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

export default nextConfig;
