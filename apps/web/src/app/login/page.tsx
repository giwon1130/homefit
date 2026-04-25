"use client";

import { signIn } from "next-auth/react";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";

const FEATURES: Array<{ emoji: string; title: string; desc: string }> = [
  {
    emoji: "✓",
    title: "자격 자동 판정",
    desc: "신혼부부 · 생애최초 · 다자녀 중 어느 유형이 유리한지 알려드려요.",
  },
  {
    emoji: "🚉",
    title: "통근 가능성 점수",
    desc: "본인+배우자 직장에서 대중교통 도어투도어 시간으로 매칭.",
  },
  {
    emoji: "♥",
    title: "내 조건 맞춤 정렬",
    desc: "예산 · 지역 · 자격 · 통근을 합산해 점수 높은 청약을 위로.",
  },
];

function LoginContent() {
  const params = useSearchParams();
  const error = params.get("error");
  const callbackUrl = params.get("callbackUrl") ?? "/match";

  return (
    <div className="-mx-4 -my-6 min-h-[calc(100vh-3.5rem)] bg-gradient-to-b from-blue-50 via-white to-white">
      <div className="mx-auto max-w-3xl px-4 py-10">
        <section className="text-center">
          <p className="inline-block rounded-full bg-blue-100 px-3 py-1 text-xs font-medium text-blue-700">
            신혼부부 · 자녀 있는 가족 청약 매칭
          </p>
          <h1 className="mt-4 text-3xl font-bold tracking-tight sm:text-4xl">
            내 조건에 맞는 청약,
            <br />
            <span className="text-blue-600">출퇴근 가능한 곳</span>만 골라드려요
          </h1>
          <p className="mx-auto mt-3 max-w-md text-sm text-zinc-600">
            전국 청약 공고를 매일 가져와 내 자격·예산·통근으로 자동 정렬.
          </p>
        </section>

        <section className="mx-auto mt-8 max-w-sm rounded-xl border border-zinc-200 bg-white p-6 shadow-sm">
          {error && (
            <div className="mb-3 rounded border border-red-200 bg-red-50 p-3 text-xs text-red-700">
              로그인 실패: {error}
            </div>
          )}
          <button
            onClick={() => signIn("google", { callbackUrl })}
            className="flex w-full items-center justify-center gap-2 rounded-lg bg-white border border-zinc-300 px-4 py-3 font-medium text-zinc-800 hover:bg-zinc-50"
          >
            <GoogleIcon />
            Google로 계속하기
          </button>
          <p className="mt-3 text-center text-xs text-zinc-500">
            처음이라면 자동으로 가입돼요. 별도 회원가입 절차 없음.
          </p>
        </section>

        <section className="mx-auto mt-10 grid max-w-2xl gap-3 sm:grid-cols-3">
          {FEATURES.map((f) => (
            <div
              key={f.title}
              className="rounded-lg border border-zinc-200 bg-white p-4 text-center"
            >
              <div className="text-2xl">{f.emoji}</div>
              <h3 className="mt-2 text-sm font-semibold">{f.title}</h3>
              <p className="mt-1 text-xs text-zinc-600 leading-relaxed">{f.desc}</p>
            </div>
          ))}
        </section>

        <footer className="mx-auto mt-10 max-w-2xl text-center text-xs text-zinc-500">
          <p>
            소득 · 자산 같은 민감 정보는 AES-256으로 암호화돼 저장돼요.
            <br className="hidden sm:inline" />
            서비스 이용은 무료이며, 청약 신청은 청약홈에서 직접 진행해야 합니다.
          </p>
        </footer>
      </div>
    </div>
  );
}

function GoogleIcon() {
  return (
    <svg viewBox="0 0 18 18" className="h-5 w-5" aria-hidden>
      <path
        fill="#4285F4"
        d="M17.64 9.205c0-.639-.057-1.252-.164-1.841H9v3.481h4.844c-.209 1.125-.843 2.078-1.796 2.717v2.258h2.908c1.702-1.567 2.684-3.875 2.684-6.615z"
      />
      <path
        fill="#34A853"
        d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.258c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332C2.438 15.983 5.482 18 9 18z"
      />
      <path
        fill="#FBBC05"
        d="M3.964 10.71c-.18-.54-.282-1.117-.282-1.71s.102-1.17.282-1.71V4.958H.957C.347 6.173 0 7.548 0 9s.348 2.827.957 4.042l3.007-2.332z"
      />
      <path
        fill="#EA4335"
        d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0 5.482 0 2.438 2.017.957 4.958L3.964 7.29C4.672 5.163 6.656 3.58 9 3.58z"
      />
    </svg>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<div>로딩…</div>}>
      <LoginContent />
    </Suspense>
  );
}
