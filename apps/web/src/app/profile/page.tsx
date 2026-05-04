import { redirect } from "next/navigation";
import { auth } from "@/auth";
import { apiFetch, type FullProfile, type Income, type ScoreResp } from "@/lib/api";
import ProfileForm from "./ProfileForm";
import NotificationPreferences from "./NotificationPreferences";

export const dynamic = "force-dynamic";

export default async function ProfilePage() {
  const session = await auth();
  if (!session?.accessToken) redirect("/login");

  const [profileRes, scoreRes, notifRes] = await Promise.all([
    apiFetch("/api/v1/profile"),
    apiFetch("/api/v1/profile/score"),
    apiFetch("/api/v1/notifications/preferences"),
  ]);
  if (!profileRes.ok) {
    return (
      <div className="rounded border border-red-200 bg-red-50 p-4 text-red-700">
        프로필 로드 실패 (HTTP {profileRes.status})
      </div>
    );
  }
  const profile: FullProfile = await profileRes.json();
  const score: ScoreResp | null = scoreRes.ok ? await scoreRes.json() : null;
  const notifPref: { emailEnabled: boolean; pushEnabled: boolean } = notifRes.ok
    ? await notifRes.json()
    : { emailEnabled: true, pushEnabled: true };

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-bold">내 프로필</h1>
        <p className="mt-1 text-sm text-zinc-500">
          입력하신 정보로 자격 판정과 가점이 자동 계산됩니다.
        </p>
      </header>

      {score && <ScoreCard score={score} />}
      {profile.incomes.length > 0 && <IncomeTrend incomes={profile.incomes} />}
      <NotificationPreferences
        initialEmailEnabled={notifPref.emailEnabled}
        initialPushEnabled={notifPref.pushEnabled}
      />

      <ProfileForm
        initialCore={profile.core}
        initialMembers={profile.householdMembers}
        initialWorkplaces={profile.workplaces}
        initialIncomes={profile.incomes}
        initialAssets={profile.assets}
        initialPreferences={profile.preferences}
      />
    </div>
  );
}

function IncomeTrend({ incomes }: { incomes: Income[] }) {
  const sorted = [...incomes].sort((a, b) => a.year - b.year);
  const totals = sorted.map((i) => ({
    year: i.year,
    self: i.selfAmount ?? 0,
    spouse: i.spouseAmount ?? 0,
    total: (i.selfAmount ?? 0) + (i.spouseAmount ?? 0),
  }));
  const max = Math.max(1, ...totals.map((t) => t.total));
  const fmt = (n: number) => {
    if (n === 0) return "-";
    const eok = Math.floor(n / 100_000_000);
    const man = Math.floor((n % 100_000_000) / 10_000);
    if (eok > 0) return `${eok}억${man > 0 ? ` ${man.toLocaleString()}만` : ""}`;
    return `${man.toLocaleString()}만`;
  };

  return (
    <section className="rounded-lg border border-zinc-200 bg-white p-4">
      <h2 className="mb-3 text-sm font-semibold text-zinc-700">소득 추이 (부부합산, 만원 단위)</h2>
      <div className="space-y-2">
        {totals.map((t) => {
          const selfPct = (t.self / max) * 100;
          const spousePct = (t.spouse / max) * 100;
          return (
            <div key={t.year} className="flex items-center gap-3 text-xs">
              <span className="w-12 shrink-0 text-zinc-500">{t.year}</span>
              <div className="flex h-5 flex-1 overflow-hidden rounded bg-zinc-100">
                <div className="h-full bg-blue-500" style={{ width: `${selfPct}%` }} />
                <div className="h-full bg-emerald-500" style={{ width: `${spousePct}%` }} />
              </div>
              <span className="w-32 shrink-0 text-right font-medium tabular-nums">
                {fmt(t.total)}
              </span>
            </div>
          );
        })}
      </div>
      <div className="mt-3 flex gap-3 text-xs text-zinc-500">
        <span className="flex items-center gap-1">
          <span className="inline-block h-2 w-3 rounded-sm bg-blue-500" />본인
        </span>
        <span className="flex items-center gap-1">
          <span className="inline-block h-2 w-3 rounded-sm bg-emerald-500" />배우자
        </span>
      </div>
    </section>
  );
}

function ScoreCard({ score }: { score: ScoreResp }) {
  const items = [
    { label: "무주택기간", item: score.breakdown.noHomePeriod },
    { label: "부양가족", item: score.breakdown.dependents },
    { label: "청약통장", item: score.breakdown.accountAge },
  ];
  return (
    <section className="rounded-lg border border-blue-200 bg-blue-50 p-4">
      <div className="flex items-baseline justify-between">
        <h2 className="font-semibold">청약 가점</h2>
        <div>
          <span className="text-3xl font-bold">{score.total}</span>
          <span className="text-sm text-zinc-500"> / {score.max}</span>
        </div>
      </div>
      <div className="mt-3 grid gap-2 sm:grid-cols-3">
        {items.map(({ label, item }) => {
          const pct = item.max > 0 ? Math.round((item.points / item.max) * 100) : 0;
          return (
            <div key={label} className="rounded bg-white p-3">
              <div className="flex items-baseline justify-between">
                <div className="text-xs text-zinc-500">{label}</div>
                <div className="text-sm font-semibold">
                  {item.points}
                  <span className="text-zinc-400"> / {item.max}</span>
                </div>
              </div>
              <div className="mt-1 h-1.5 overflow-hidden rounded bg-zinc-100">
                <div
                  className="h-full bg-blue-500 transition-all"
                  style={{ width: `${pct}%` }}
                />
              </div>
              <p className="mt-2 text-xs leading-snug text-zinc-600">{item.reason}</p>
            </div>
          );
        })}
      </div>
      {score.notes.length > 0 && (
        <ul className="mt-3 list-disc pl-5 text-xs text-zinc-600">
          {score.notes.map((n, i) => (
            <li key={i}>{n}</li>
          ))}
        </ul>
      )}
    </section>
  );
}
