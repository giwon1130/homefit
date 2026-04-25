import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // typedRoutes 는 동적 callbackUrl 처리 시 마찰이 커서 비활성. 정적 라우트만 쓸 때 다시 켜기.
};

export default nextConfig;
