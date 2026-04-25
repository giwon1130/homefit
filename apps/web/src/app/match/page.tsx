import { redirect } from "next/navigation";
import Link from "next/link";
import { auth } from "@/auth";
import { apiFetch, type FullProfile, type MatchedListingPage } from "@/lib/api";
import { isProfileEmpty } from "@/lib/profile";

export const dynamic = "force-dynamic";

const LISTING_TYPE_LABEL: Record<string, string> = {
  PRIVATE_SALE: "민영분양",
  PUBLIC_SALE: "공공분양",
  NEWLYWED_HOPE: "신혼희망타운",
  HAPPY_HOUSE: "행복주택",
  PURCHASE_RENTAL: "매입임대",
  JEONSE_RENTAL: "전세임대",
  NATIONAL_RENTAL: "국민임대",
  OTHER: "기타",
};

const SUPPLY_TYPE_LABEL: Record<string, string> = {
  GENERAL: "일반공급",
  FIRST_TIME: "생애최초",
  NEWLYWED: "신혼부부",
  MULTI_CHILD: "다자녀",
};

function formatDate(iso?: string) {
  if (!iso) return "-";
  try {
    return new Date(iso).toLocaleDateString("ko-KR", { month: "2-digit", day: "2-digit" });
  } catch {
    return iso;
  }
}

function daysUntil(iso?: string): string {
  if (!iso) return "";
  const diff = Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000);
  if (diff < 0) return "마감";
  if (diff === 0) return "오늘";
  return `D-${diff}`;
}

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

  return (
    <div className="space-y-4">
      <header className="flex items-end justify-between">
        <div>
          <h1 className="text-2xl font-bold">내 맞춤 청약</h1>
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

      {page.content.length === 0 ? (
        <div className="rounded border border-zinc-200 bg-white p-8 text-center text-zinc-500">
          매칭되는 청약이 없습니다. <Link href="/profile" className="text-blue-600 hover:underline">프로필</Link>을 채워보세요.
        </div>
      ) : (
        <ul className="space-y-3">
          {page.content.map(({ listing: l, score }) => (
            <li key={l.id}>
              <Link
                href={`/listings/${l.id}`}
                className="block rounded-lg border border-zinc-200 bg-white p-4 hover:border-blue-400 hover:shadow-sm"
              >
                <div className="flex items-start gap-4">
                  <ScoreBadge total={score.total} max={score.max} />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 text-xs">
                      <span className="rounded bg-blue-50 px-2 py-0.5 text-blue-700">
                        {LISTING_TYPE_LABEL[l.listingType] ?? l.listingType}
                      </span>
                      {score.bestSupplyType && (
                        <span className="rounded bg-emerald-50 px-2 py-0.5 text-emerald-700">
                          {SUPPLY_TYPE_LABEL[score.bestSupplyType]}
                        </span>
                      )}
                      {l.sido && (
                        <span className="text-zinc-500">
                          {l.sido} {l.sigungu}
                        </span>
                      )}
                    </div>
                    <h2 className="mt-1 font-semibold">{l.name}</h2>
                    <div className="mt-1 grid grid-cols-4 gap-2 text-xs text-zinc-500">
                      <Bar label="자격" v={score.eligibility} max={25} />
                      <Bar label="예산" v={score.budget} max={25} />
                      <Bar label="지역" v={score.region} max={20} />
                      <Bar
                        label={score.commuteMinutes != null ? `통근 ${score.commuteMinutes}분` : "통근"}
                        v={score.commute}
                        max={30}
                      />
                    </div>
                    <div className="mt-2 text-xs text-zinc-500">
                      접수 {formatDate(l.applicationStart)} ~ {formatDate(l.applicationEnd)} ({daysUntil(l.applicationEnd)})
                    </div>
                  </div>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function ScoreBadge({ total, max }: { total: number; max: number }) {
  const ratio = max ? total / max : 0;
  const color = ratio >= 0.7 ? "bg-emerald-600" : ratio >= 0.4 ? "bg-blue-600" : "bg-zinc-400";
  return (
    <div className={`flex h-14 w-14 shrink-0 flex-col items-center justify-center rounded-full text-white ${color}`}>
      <div className="text-lg font-bold leading-none">{total}</div>
      <div className="text-[10px] opacity-80">/ {max}</div>
    </div>
  );
}

function Bar({ label, v, max }: { label: string; v: number; max: number }) {
  const pct = max ? Math.round((v / max) * 100) : 0;
  return (
    <div>
      <div className="flex justify-between">
        <span>{label}</span>
        <span className="text-zinc-700">
          {v}/{max}
        </span>
      </div>
      <div className="mt-0.5 h-1 w-full overflow-hidden rounded bg-zinc-100">
        <div className="h-full bg-blue-500" style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}
