import type { Metadata } from "next";
import "./globals.css";
import { SessionProvider } from "next-auth/react";
import Header from "@/components/Header";

export const metadata: Metadata = {
  title: "homefit",
  description: "내 조건에 맞는 청약 · 전세 · 매매 추천",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body className="min-h-screen">
        <SessionProvider>
          <Header />
          <main className="mx-auto max-w-3xl px-4 py-6">{children}</main>
        </SessionProvider>
      </body>
    </html>
  );
}
