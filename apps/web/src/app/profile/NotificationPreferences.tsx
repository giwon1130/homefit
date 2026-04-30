"use client";

import { useState, useTransition } from "react";
import { setEmailNotifications, setPushNotifications } from "./actions";

export default function NotificationPreferences({
  initialEmailEnabled,
  initialPushEnabled,
}: {
  initialEmailEnabled: boolean;
  initialPushEnabled: boolean;
}) {
  const [email, setEmail] = useState(initialEmailEnabled);
  const [push, setPush] = useState(initialPushEnabled);
  const [pending, startTransition] = useTransition();
  const [error, setError] = useState<string | null>(null);

  const toggleEmail = (next: boolean) => {
    setError(null);
    setEmail(next);
    startTransition(async () => {
      const r = await setEmailNotifications(next);
      if (!r.ok) {
        setEmail(!next);
        setError(r.error);
      }
    });
  };

  const togglePush = (next: boolean) => {
    setError(null);
    setPush(next);
    startTransition(async () => {
      const r = await setPushNotifications(next);
      if (!r.ok) {
        setPush(!next);
        setError(r.error);
      }
    });
  };

  return (
    <section className="rounded-lg border border-zinc-200 bg-white p-4">
      <h2 className="text-sm font-semibold text-zinc-700">알림</h2>
      <p className="mt-1 text-xs text-zinc-500">
        즐겨찾기한 청약의 접수 마감 1일 전 알려드려요.
      </p>

      <Row
        label="D-1 이메일 알림"
        sub="가입한 이메일로 발송됩니다."
        on={email}
        onToggle={toggleEmail}
        disabled={pending}
      />
      <Row
        label="D-1 푸시 알림"
        sub="모바일 앱에 푸시로 발송됩니다. (앱에서 권한 허용 필요)"
        on={push}
        onToggle={togglePush}
        disabled={pending}
      />

      {error && <p className="mt-2 text-xs text-red-600">{error}</p>}
    </section>
  );
}

function Row({
  label,
  sub,
  on,
  onToggle,
  disabled,
}: {
  label: string;
  sub?: string;
  on: boolean;
  onToggle: (next: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <div className="mt-3 flex items-center justify-between border-t border-zinc-100 pt-3 first:border-t-0 first:pt-0">
      <div>
        <span className="text-sm text-zinc-700">{label}</span>
        {sub && <p className="text-xs text-zinc-500">{sub}</p>}
      </div>
      <button
        type="button"
        onClick={() => onToggle(!on)}
        disabled={disabled}
        aria-pressed={on}
        className={
          on
            ? "relative h-6 w-11 shrink-0 rounded-full bg-blue-600 transition disabled:opacity-50"
            : "relative h-6 w-11 shrink-0 rounded-full bg-zinc-300 transition disabled:opacity-50"
        }
      >
        <span
          className={
            on
              ? "absolute top-0.5 left-[22px] h-5 w-5 rounded-full bg-white shadow transition"
              : "absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white shadow transition"
          }
        />
      </button>
    </div>
  );
}
