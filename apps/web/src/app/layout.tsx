import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "homefit",
  description: "내 조건에 맞는 청약/전세/매매 추천",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
