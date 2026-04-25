import { redirect } from "next/navigation";
import { auth } from "@/auth";
import { apiFetch, type FullProfile, type ScoreResp } from "@/lib/api";
import ProfileForm from "./ProfileForm";

export const dynamic = "force-dynamic";

export default async function ProfilePage() {
  const session = await auth();
  if (!session?.accessToken) redirect("/login");

  const [profileRes, scoreRes] = await Promise.all([
    apiFetch("/api/v1/profile"),
    apiFetch("/api/v1/profile/score"),
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

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-bold">내 프로필</h1>
        <p className="mt-1 text-sm text-zinc-500">
          입력하신 정보로 자격 판정과 가점이 자동 계산됩니다.
        </p>
      </header>

      {score && <ScoreCard score={score} />}

      <ProfileForm
        initialCore={profile.core}
        initialMembers={profile.householdMembers}
        initialWorkplaces={profile.workplaces}
      />
    </div>
  );
}

function ScoreCard({ score }: { score: ScoreResp }) {
  const items = [
    { label: "무주택기간", ...score.breakdown.noHomePeriod },
    { label: "부양가족", ...score.breakdown.dependents },
    { label: "청약통장", ...score.breakdown.accountAge },
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
      <div className="mt-3 grid grid-cols-3 gap-2">
        {items.map((it) => (
          <div key={it.label} className="rounded bg-white p-2 text-center">
            <div className="text-xs text-zinc-500">{it.label}</div>
            <div className="mt-1 text-sm font-semibold">
              {it.points} <span className="text-zinc-400">/ {it.max}</span>
            </div>
          </div>
        ))}
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
