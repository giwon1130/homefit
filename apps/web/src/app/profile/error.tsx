"use client";

import { useEffect } from "react";

/**
 * /profile 라우트의 server-side 예외를 잡는 error boundary.
 * Application error 화면 대신 사용자에게 친절한 메시지 + 재시도 버튼.
 */
export default function ProfileError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("profile page error", error);
  }, [error]);

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">내 프로필</h1>
      <div className="rounded border border-red-200 bg-red-50 p-4 text-sm text-red-800">
        프로필을 불러오는 중 오류가 발생했어요. 잠시 후 다시 시도해주세요.
        {error.digest && (
          <div className="mt-2 text-xs text-red-600">에러 ID: {error.digest}</div>
        )}
        <button
          onClick={reset}
          className="mt-3 rounded bg-red-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-red-700"
        >
          다시 시도
        </button>
      </div>
    </div>
  );
}
