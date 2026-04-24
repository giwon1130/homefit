"use client";

import { signIn } from "next-auth/react";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";

function LoginContent() {
  const params = useSearchParams();
  const error = params.get("error");

  return (
    <div className="mx-auto mt-16 max-w-sm space-y-6 text-center">
      <h1 className="text-2xl font-bold">homefit 로그인</h1>
      <p className="text-sm text-zinc-500">
        내 조건에 맞는 청약만 골라 드려요.
      </p>

      {error && (
        <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          로그인 실패: {error}
        </div>
      )}

      <button
        onClick={() => signIn("google", { callbackUrl: "/" })}
        className="w-full rounded bg-blue-600 px-4 py-3 font-medium text-white hover:bg-blue-700"
      >
        Google 로 계속하기
      </button>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<div>로딩…</div>}>
      <LoginContent />
    </Suspense>
  );
}
