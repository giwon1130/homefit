import Link from "next/link";
import { auth } from "@/auth";
import {
  apiFetch,
  type ListingPage,
  type ListingType,
  type MatchedListingPage,
  type FullProfile,
} from "@/lib/api";
import ListingMap, { type MapPoint } from "@/components/ListingMap";

export const dynamic = "force-dynamic";
export const metadata = { title: "homefit · 지도" };

const SIDO_OPTIONS = [
  "서울", "경기", "인천", "부산", "대구", "대전", "광주", "충남", "충북",
  "전남", "전북", "경북", "경남", "강원", "제주", "세종", "울산",
] as const;

const TYPE_OPTIONS: ListingType[] = [
  "PRIVATE_SALE",
  "PUBLIC_SALE",
  "NEWLYWED_HOPE",
  "HAPPY_HOUSE",
  "PURCHASE_RENTAL",
  "JEONSE_RENTAL",
  "NATIONAL_RENTAL",
];

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

function sidoFullName(s: string): string {
  const m: Record<string, string> = {
    서울: "서울특별시", 부산: "부산광역시", 대구: "대구광역시", 인천: "인천광역시",
    광주: "광주광역시", 대전: "대전광역시", 울산: "울산광역시", 세종: "세종특별자치시",
    경기: "경기도", 강원: "강원특별자치도", 충북: "충청북도", 충남: "충청남도",
    전북: "전북특별자치도", 전남: "전라남도", 경북: "경상북도", 경남: "경상남도",
    제주: "제주특별자치도",
  };
  return m[s] ?? s;
}

type SearchParamValue = string | string[] | undefined;
type Props = { searchParams: Promise<Record<string, SearchParamValue>> };

function pickFirst(v: SearchParamValue): string | undefined {
  if (Array.isArray(v)) return v[0];
  return v;
}
function pickAll(v: SearchParamValue): string[] {
  if (v === undefined) return [];
  return Array.isArray(v) ? v : [v];
}
function buildHref(p: { sido?: string; types?: string[]; matchOnly?: boolean }): string {
  const u = new URLSearchParams();
  if (p.sido) u.set("sido", p.sido);
  for (const t of p.types ?? []) u.append("type", t);
  if (p.matchOnly) u.set("matchOnly", "1");
  const q = u.toString();
  return q ? `/map?${q}` : "/map";
}

export default async function MapPage({ searchParams }: Props) {
  const sp = await searchParams;
  const sido = pickFirst(sp.sido);
  const types = pickAll(sp.type);
  const matchOnly = pickFirst(sp.matchOnly) === "1";
  const session = await auth();
  const authed = !!session?.accessToken;

  // 매칭 모드 + 로그인이면 /match 결과 사용 (점수 색상), 아니면 /listings.
  const apiParams = new URLSearchParams();
  apiParams.set("size", "200");      // 지도는 한 번에 충분히 많이
  if (sido) apiParams.set("sido", sidoFullName(sido));
  for (const t of types) apiParams.append("type", t);

  let mapPoints: MapPoint[] = [];
  if (authed && matchOnly) {
    apiParams.set("size", "100");
    const r = await apiFetch(`/api/v1/listings/match?${apiParams.toString()}`);
    if (r.ok) {
      const data = (await r.json()) as MatchedListingPage;
      mapPoints = data.content
        .filter((m) => m.listing.latitude != null && m.listing.longitude != null)
        .map((m) => ({
          id: m.listing.id,
          lat: Number(m.listing.latitude),
          lng: Number(m.listing.longitude),
          title: m.listing.name,
          subtitle: [m.listing.sido, m.listing.sigungu].filter(Boolean).join(" "),
          score: m.score.total,
          totalSupply: m.listing.totalSupply ?? undefined,
          href: `/listings/${m.listing.id}`,
        }));
    }
  } else {
    apiParams.set("sort", "CLOSING");
    const r = await apiFetch(`/api/v1/listings?${apiParams.toString()}`);
    if (r.ok) {
      const data = (await r.json()) as ListingPage;
      mapPoints = data.content
        .filter((l) => l.latitude != null && l.longitude != null)
        .map((l) => ({
          id: l.id,
          lat: Number(l.latitude),
          lng: Number(l.longitude),
          title: l.name,
          subtitle: [l.sido, l.sigungu].filter(Boolean).join(" "),
          totalSupply: l.totalSupply ?? undefined,
          href: `/listings/${l.id}`,
        }));
    }
  }

  // 직장 좌표 (로그인 시)
  let workplaces: Array<{ lat: number; lng: number; label: string }> = [];
  if (authed) {
    const pr = await apiFetch("/api/v1/profile");
    if (pr.ok) {
      const profile = (await pr.json()) as FullProfile;
      workplaces = profile.workplaces
        .filter((w) => w.latitude != null && w.longitude != null)
        .map((w) => ({
          lat: Number(w.latitude),
          lng: Number(w.longitude),
          label: w.label || (w.owner === "SELF" ? "본인 직장" : "배우자 직장"),
        }));
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-end justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">청약 단지 지도</h1>
          <p className="mt-1 text-sm text-zinc-500">
            좌표가 확보된 단지만 표시. 직장 좌표(🏢) 와 함께 통근권을 한눈에.
          </p>
        </div>
        <span className="text-sm text-zinc-500">
          {mapPoints.length}개 단지
        </span>
      </div>

      {/* 시도 필터 */}
      <div className="space-y-2">
        <div className="text-xs font-medium text-zinc-600">지역</div>
        <div className="flex flex-wrap gap-2">
          <ChipLink active={!sido} href={buildHref({ types, matchOnly })}>전체</ChipLink>
          {SIDO_OPTIONS.map((s) => (
            <ChipLink
              key={s}
              active={sido === s}
              href={buildHref({ sido: sido === s ? undefined : s, types, matchOnly })}
            >
              {s}
            </ChipLink>
          ))}
        </div>
      </div>

      <div className="space-y-2">
        <div className="text-xs font-medium text-zinc-600">공급 유형</div>
        <div className="flex flex-wrap gap-2">
          <ChipLink active={types.length === 0} href={buildHref({ sido, matchOnly })}>전체</ChipLink>
          {TYPE_OPTIONS.map((t) => {
            const isOn = types.includes(t);
            const next = isOn ? types.filter((x) => x !== t) : [...types, t];
            return (
              <ChipLink
                key={t}
                active={isOn}
                href={buildHref({ sido, types: next, matchOnly })}
              >
                {LISTING_TYPE_LABEL[t]}
              </ChipLink>
            );
          })}
        </div>
      </div>

      {authed && (
        <div className="flex items-center gap-2 text-xs text-zinc-600">
          <span>모드:</span>
          <ChipLink active={!matchOnly} href={buildHref({ sido, types })}>전체 단지</ChipLink>
          <ChipLink active={matchOnly} href={buildHref({ sido, types, matchOnly: true })}>
            내 매칭 점수 (색상 강조)
          </ChipLink>
        </div>
      )}

      <ListingMap
        points={mapPoints}
        workplaces={workplaces}
        className="h-[640px]"
      />

      {authed && matchOnly && (
        <Legend />
      )}
    </div>
  );
}

function ChipLink({
  active,
  href,
  children,
}: {
  active: boolean;
  href: string;
  children: React.ReactNode;
}) {
  const cls = active
    ? "rounded-full border border-blue-600 bg-blue-600 px-3 py-1 text-xs font-medium text-white"
    : "rounded-full border border-zinc-300 bg-white px-3 py-1 text-xs text-zinc-700 hover:bg-zinc-50";
  return (
    <Link href={href} className={cls}>
      {children}
    </Link>
  );
}

function Legend() {
  const items = [
    { color: "#10b981", label: "70+ 점 (유력 매칭)" },
    { color: "#3b82f6", label: "50~69 점" },
    { color: "#f59e0b", label: "30~49 점" },
    { color: "#94a3b8", label: "29 점 이하 / 정보 부족" },
  ];
  return (
    <div className="rounded border border-zinc-200 bg-white p-3 text-xs text-zinc-600">
      <div className="font-medium text-zinc-700">점수별 색상</div>
      <div className="mt-2 flex flex-wrap gap-3">
        {items.map((it) => (
          <span key={it.label} className="flex items-center gap-1">
            <span
              className="inline-block h-3 w-3 rounded-full"
              style={{ backgroundColor: it.color }}
            />
            {it.label}
          </span>
        ))}
        <span className="flex items-center gap-1">
          <span className="inline-block h-3 w-3 rounded-sm bg-teal-600 text-[10px] leading-3 text-white">
            🏢
          </span>
          내/배우자 직장
        </span>
      </div>
    </div>
  );
}
