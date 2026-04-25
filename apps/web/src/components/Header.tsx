"use client";

import Link from "next/link";
import { useSession, signIn, signOut } from "next-auth/react";

export default function Header() {
  const { data: session, status } = useSession();
  return (
    <header className="border-b border-zinc-200 bg-white">
      <div className="mx-auto flex max-w-3xl items-center justify-between px-4 py-3">
        <Link href="/" className="text-xl font-bold tracking-tight">
          homefit
        </Link>
        <nav className="flex items-center gap-4 text-sm">
          <Link href="/" className="text-zinc-600 hover:text-zinc-900">
            청약목록
          </Link>
          {status === "authenticated" ? (
            <>
              <Link href="/match" className="text-zinc-600 hover:text-zinc-900">
                내맞춤
              </Link>
              <Link href="/profile" className="text-zinc-600 hover:text-zinc-900">
                프로필
              </Link>
              <span className="hidden text-zinc-600 sm:inline">
                {session.user?.name ?? session.user?.email}
              </span>
              <button
                className="rounded border border-zinc-300 px-3 py-1 text-zinc-700 hover:bg-zinc-50"
                onClick={() => signOut()}
              >
                로그아웃
              </button>
            </>
          ) : status === "loading" ? (
            <span className="text-zinc-400">...</span>
          ) : (
            <button
              className="rounded bg-blue-600 px-3 py-1 text-white hover:bg-blue-700"
              onClick={() => signIn("google")}
            >
              Google 로그인
            </button>
          )}
        </nav>
      </div>
    </header>
  );
}
