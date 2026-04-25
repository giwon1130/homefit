import { redirect } from "next/navigation";
import Link from "next/link";
import { auth } from "@/auth";
import { apiFetch, type FullProfile, type MatchedListingPage } from "@/lib/api";
import { isProfileEmpty } from "@/lib/profile";
import MatchInteractive from "./MatchInteractive";

export const dynamic = "force-dynamic";

export default async function MatchPage() {
  const session = await auth();
  if (!session?.accessToken) redirect("/login");

  const [matchRes, profileRes] = await Promise.all([
    apiFetch("/api/v1/listings/match?size=20"),
    apiFetch("/api/v1/profile"),
  ]);
  if (!matchRes.ok) {
    return (
      <div className="rounded border border-red-200 bg-red-50 p-4 text-red-700">
        매칭 결과를 불러오지 못했습니다 (HTTP {matchRes.status}).
      </div>
    );
  }
  const page: MatchedListingPage = await matchRes.json();
  const profile: FullProfile | null = profileRes.ok ? await profileRes.json() : null;
  const profileEmpty = profile ? isProfileEmpty(profile) : false;

  const workplaces =
    profile?.workplaces
      .filter((w) => w.latitude != null && w.longitude != null)
      .map((w) => ({
        lat: Number(w.latitude),
        lng: Number(w.longitude),
        label: w.label || (w.owner === "SELF" ? "본인 직장" : "배우자 직장"),
      })) ?? [];

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">내 맞춤 청약</h1>
          <p className="mt-1 text-sm text-zinc-500">
            자격(25) + 예산(25) + 지역(20) + 통근(30) 조합 점수 순.
          </p>
        </div>
        <Link href="/" className="text-sm text-zinc-500 hover:text-zinc-900">
          전체 목록 →
        </Link>
      </header>

      {profileEmpty && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm">
          <strong className="text-amber-900">⚠ 프로필 미입력</strong>
          <p className="mt-1 text-amber-800">
            자격 / 예산 / 통근 점수는 프로필 정보가 있어야 정확합니다. 지금은 기본값으로 매칭 중이에요.
          </p>
          <Link
            href="/onboarding"
            className="mt-2 inline-block rounded bg-amber-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-amber-700"
          >
            프로필 채우기 →
          </Link>
        </div>
      )}

      <MatchInteractive
        matches={page.content}
        workplaces={workplaces}
        polygons={[]}
      />
    </div>
  );
}
