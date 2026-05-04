import Link from "next/link";
import { apiFetch, type ListingPage, type ListingType } from "@/lib/api";
import SearchControls from "./SearchControls";

export const dynamic = "force-dynamic";

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
  const diff = Math.ceil((new Date(iso).getTime() - Date.now()) / (1000 * 60 * 60 * 24));
  if (diff < 0) return "마감";
  if (diff === 0) return "오늘";
  return `D-${diff}`;
}

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

const SIDO_OPTIONS = ["서울", "경기", "인천", "부산", "대구", "대전", "광주", "충남", "충북", "전남", "전북", "경북", "경남", "강원", "제주", "세종", "울산"] as const;
const TYPE_OPTIONS: ListingType[] = [
  "PRIVATE_SALE",
  "PUBLIC_SALE",
  "NEWLYWED_HOPE",
  "HAPPY_HOUSE",
  "PURCHASE_RENTAL",
  "JEONSE_RENTAL",
  "NATIONAL_RENTAL",
];

const SORT_OPTIONS: Array<{ v: string; label: string }> = [
  { v: "CLOSING", label: "마감 임박순" },
  { v: "ANNOUNCEMENT", label: "최근 공고순" },
  { v: "MOVE_IN", label: "입주 빠른 순" },
  { v: "PRICE_LOW", label: "분양가 낮은 순" },
  { v: "PRICE_HIGH", label: "분양가 높은 순" },
];

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

interface FilterState {
  sido?: string;
  types?: string[];
  q?: string;
  maxPriceEok?: string;
  sort?: string;
  page?: number;
}

function buildQuery(p: FilterState): string {
  const u = new URLSearchParams();
  if (p.sido) u.set("sido", p.sido);
  for (const t of p.types ?? []) u.append("type", t);
  if (p.q) u.set("q", p.q);
  if (p.maxPriceEok) u.set("maxPriceEok", p.maxPriceEok);
  if (p.sort && p.sort !== "CLOSING") u.set("sort", p.sort);
  if (p.page) u.set("page", p.page.toString());
  return u.toString();
}

export default async function HomePage({ searchParams }: Props) {
  const sp = await searchParams;
  const sido = pickFirst(sp.sido);
  const types = pickAll(sp.type);
  const q = pickFirst(sp.q);
  const maxPriceEok = pickFirst(sp.maxPriceEok);
  const sort = pickFirst(sp.sort) ?? "CLOSING";

  // 백엔드 호출
  const apiParams = new URLSearchParams();
  apiParams.set("size", "30");
  apiParams.set("sort", sort);
  if (sido) {
    apiParams.set("sido", sidoFullName(sido));
  }
  for (const t of types) apiParams.append("type", t);
  if (q && q.trim()) apiParams.set("q", q.trim());
  if (maxPriceEok) {
    const eok = Number(maxPriceEok);
    if (Number.isFinite(eok) && eok > 0) {
      apiParams.set("maxPriceKrw", Math.round(eok * 100_000_000).toString());
    }
  }

  const res = await apiFetch(`/api/v1/listings?${apiParams.toString()}`);
  if (!res.ok) {
    return (
      <div className="rounded border border-red-200 bg-red-50 p-4 text-red-700">
        청약 목록을 불러오지 못했습니다 (HTTP {res.status}).
      </div>
    );
  }
  const page: ListingPage = await res.json();

  return (
    <div className="space-y-6">
      <div className="flex items-end justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">진행 중인 청약</h1>
          <p className="mt-1 text-sm text-zinc-500">
            모집공고 마감 임박순. 로그인하면 내 조건 맞춤 정렬을 볼 수 있어요.
          </p>
        </div>
        <span className="text-sm text-zinc-500">총 {page.total}건</span>
      </div>

      {/* 검색 + 가격대 + 정렬 (클라이언트 form) */}
      <SearchControls
        initialQ={q ?? ""}
        initialMaxPriceEok={maxPriceEok ?? ""}
        initialSort={sort}
        sortOptions={SORT_OPTIONS}
        sido={sido}
        types={types}
      />

      {/* 시도 필터 */}
      <div className="space-y-2">
        <div className="text-xs font-medium text-zinc-600">지역</div>
        <div className="flex flex-wrap gap-2">
          <ChipLink active={!sido} href={`/?${buildQuery({ types, q, maxPriceEok, sort })}`}>
            전체
          </ChipLink>
          {SIDO_OPTIONS.map((s) => (
            <ChipLink
              key={s}
              active={sido === s}
              href={`/?${buildQuery({ sido: sido === s ? undefined : s, types, q, maxPriceEok, sort })}`}
            >
              {s}
            </ChipLink>
          ))}
        </div>
      </div>

      {/* 유형 필터 */}
      <div className="space-y-2">
        <div className="text-xs font-medium text-zinc-600">공급 유형</div>
        <div className="flex flex-wrap gap-2">
          <ChipLink active={types.length === 0} href={`/?${buildQuery({ sido, q, maxPriceEok, sort })}`}>
            전체
          </ChipLink>
          {TYPE_OPTIONS.map((t) => {
            const isOn = types.includes(t);
            const next = isOn ? types.filter((x) => x !== t) : [...types, t];
            return (
              <ChipLink
                key={t}
                active={isOn}
                href={`/?${buildQuery({ sido, types: next, q, maxPriceEok, sort })}`}
              >
                {LISTING_TYPE_LABEL[t]}
              </ChipLink>
            );
          })}
        </div>
      </div>

      {page.content.length === 0 ? (
        <div className="rounded border border-zinc-200 bg-white p-8 text-center text-zinc-500">
          조건에 맞는 청약이 없습니다.
        </div>
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {page.content.map((l) => (
            <li key={l.id}>
              <Link
                href={`/listings/${l.id}`}
                className="flex h-full flex-col rounded-lg border border-zinc-200 bg-white p-4 hover:border-blue-400 hover:shadow-sm"
              >
                <div className="flex items-center gap-2 text-xs">
                  <span className="rounded bg-blue-50 px-2 py-0.5 text-blue-700">
                    {LISTING_TYPE_LABEL[l.listingType] ?? l.listingType}
                  </span>
                  <span className="font-semibold text-zinc-900">
                    {daysUntil(l.applicationEnd)}
                  </span>
                </div>
                <h2 className="mt-2 line-clamp-2 font-semibold leading-snug">{l.name}</h2>
                <p className="mt-1 text-xs text-zinc-500">
                  {l.sido && (
                    <>
                      {l.sido} {l.sigungu ?? ""}
                    </>
                  )}
                </p>
                <div className="mt-auto pt-3 text-xs text-zinc-500">
                  접수 {formatDate(l.applicationStart)} ~ {formatDate(l.applicationEnd)}
                </div>
              </Link>
            </li>
          ))}
        </ul>
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

/** "서울" → "서울특별시" 등 풀네임 매핑. 백엔드 sido 컬럼은 풀네임. */
function sidoFullName(s: string): string {
  const m: Record<string, string> = {
    서울: "서울특별시",
    부산: "부산광역시",
    대구: "대구광역시",
    인천: "인천광역시",
    광주: "광주광역시",
    대전: "대전광역시",
    울산: "울산광역시",
    세종: "세종특별자치시",
    경기: "경기도",
    강원: "강원특별자치도",
    충북: "충청북도",
    충남: "충청남도",
    전북: "전북특별자치도",
    전남: "전라남도",
    경북: "경상북도",
    경남: "경상남도",
    제주: "제주특별자치도",
  };
  return m[s] ?? s;
}
