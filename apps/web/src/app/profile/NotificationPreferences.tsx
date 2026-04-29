"use client";

import { useState, useTransition } from "react";
import { setEmailNotifications } from "./actions";

export default function NotificationPreferences({
  initialEmailEnabled,
}: {
  initialEmailEnabled: boolean;
}) {
  const [enabled, setEnabled] = useState(initialEmailEnabled);
  const [pending, startTransition] = useTransition();
  const [error, setError] = useState<string | null>(null);

  const toggle = (next: boolean) => {
    setError(null);
    setEnabled(next); // optimistic
    startTransition(async () => {
      const r = await setEmailNotifications(next);
      if (!r.ok) {
        setEnabled(!next); // rollback
        setError(r.error);
      }
    });
  };

  return (
    <section className="rounded-lg border border-zinc-200 bg-white p-4">
      <h2 className="text-sm font-semibold text-zinc-700">알림</h2>
      <p className="mt-1 text-xs text-zinc-500">
        즐겨찾기한 청약의 접수 마감 1일 전, 가입한 이메일로 알려드려요.
      </p>
      <div className="mt-3 flex items-center justify-between">
        <span className="text-sm text-zinc-700">D-1 이메일 알림</span>
        <button
          type="button"
          onClick={() => toggle(!enabled)}
          disabled={pending}
          aria-pressed={enabled}
          className={
            enabled
              ? "relative h-6 w-11 rounded-full bg-blue-600 transition disabled:opacity-50"
              : "relative h-6 w-11 rounded-full bg-zinc-300 transition disabled:opacity-50"
          }
        >
          <span
            className={
              enabled
                ? "absolute top-0.5 left-[22px] h-5 w-5 rounded-full bg-white shadow transition"
                : "absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white shadow transition"
            }
          />
        </button>
      </div>
      {error && <p className="mt-2 text-xs text-red-600">{error}</p>}
    </section>
  );
}
