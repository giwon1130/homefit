import { redirect } from "next/navigation";
import Link from "next/link";
import { auth } from "@/auth";
import { apiFetch, type ListingDetail, type ListingSummary } from "@/lib/api";

export const dynamic = "force-dynamic";
export const metadata = { title: "homefit · 즐겨찾기 비교" };

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

const RENTAL_TYPES = new Set([
  "HAPPY_HOUSE",
  "PURCHASE_RENTAL",
  "JEONSE_RENTAL",
  "NATIONAL_RENTAL",
]);

function formatDate(iso?: string) {
  if (!iso) return "-";
  try {
    return new Date(iso).toLocaleDateString("ko-KR", { month: "2-digit", day: "2-digit" });
  } catch {
    return iso;
  }
}

function dDay(iso?: string) {
  if (!iso) return "";
  const d = Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000);
  if (d < 0) return "마감";
  if (d === 0) return "오늘";
  return `D-${d}`;
}

function dDayTone(iso?: string): string {
  if (!iso) return "text-zinc-400";
  const d = Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000);
  if (d < 0) return "text-zinc-400";
  if (d <= 3) return "text-red-700 font-semibold";
  if (d <= 14) return "text-amber-700 font-medium";
  return "text-zinc-700";
}

function formatKrw(n?: number | null) {
  if (n == null || n <= 0) return "-";
  const eok = Math.floor(n / 100_000_000);
  const man = Math.floor((n % 100_000_000) / 10_000);
  if (eok > 0) return `${eok}억${man > 0 ? ` ${man.toLocaleString()}만원` : ""}`;
  if (man > 0) return `${man.toLocaleString()}만원`;
  return `${n.toLocaleString()}원`;
}

function priceRange(d: ListingDetail): string {
  const prices = d.units.map((u) => u.priceMaxKrw).filter((n): n is number => n != null && n > 0);
  if (prices.length === 0) return "-";
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  return min === max ? formatKrw(min) : `${formatKrw(min)} ~ ${formatKrw(max)}`;
}

function depositRange(d: ListingDetail): string {
  const v = d.units.map((u) => u.depositAmount).filter((n): n is number => n != null && n > 0);
  if (v.length === 0) return "-";
  const min = Math.min(...v);
  const max = Math.max(...v);
  return min === max ? formatKrw(min) : `${formatKrw(min)} ~ ${formatKrw(max)}`;
}

function rentRange(d: ListingDetail): string {
  const v = d.units.map((u) => u.monthlyRent).filter((n): n is number => n != null && n > 0);
  if (v.length === 0) return "-";
  const min = Math.min(...v);
  const max = Math.max(...v);
  return min === max ? formatKrw(min) : `${formatKrw(min)} ~ ${formatKrw(max)}`;
}

function sizeRange(d: ListingDetail): string {
  const sizes = d.units
    .map((u) => (u.sizeM2 != null ? Number(u.sizeM2) : null))
    .filter((n): n is number => n != null && n > 0);
  if (sizes.length === 0) return "-";
  const min = Math.min(...sizes);
  const max = Math.max(...sizes);
  return min === max ? `${min.toFixed(1)}㎡` : `${min.toFixed(1)} ~ ${max.toFixed(1)}㎡`;
}

function pyungPriceRange(d: ListingDetail): string {
  const pps: number[] = [];
  for (const u of d.units) {
    if (u.priceMaxKrw == null || u.sizeM2 == null) continue;
    const m2 = Number(u.sizeM2);
    if (!Number.isFinite(m2) || m2 <= 0) continue;
    const pyung = m2 * 0.3025;
    pps.push(Math.round(u.priceMaxKrw / pyung / 10_000));
  }
  if (pps.length === 0) return "-";
  const min = Math.min(...pps);
  const max = Math.max(...pps);
  return min === max ? `${min.toLocaleString()}만원` : `${min.toLocaleString()} ~ ${max.toLocaleString()}만원`;
}

type SearchParamValue = string | string[] | undefined;

export default async function CompareFavoritesPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, SearchParamValue>>;
}) {
  const session = await auth();
  if (!session?.accessToken) redirect("/login?callbackUrl=/favorites/compare");

  const sp = await searchParams;
  const exclude = new Set(
    Array.isArray(sp.exclude) ? sp.exclude : sp.exclude ? [sp.exclude] : [],
  );

  const favRes = await apiFetch("/api/v1/favorites");
  if (!favRes.ok) {
    return (
      <div className="rounded border border-red-200 bg-red-50 p-4 text-red-700">
        즐겨찾기 로드 실패 (HTTP {favRes.status})
      </div>
    );
  }
  const favorites: ListingSummary[] = await favRes.json();
  const visible = favorites.filter((f) => !exclude.has(String(f.id)));

  if (visible.length < 2) {
    return (
      <div className="space-y-4">
        <h1 className="text-2xl font-bold">즐겨찾기 비교</h1>
        <p className="rounded border border-zinc-200 bg-white p-6 text-zinc-600">
          비교하려면 최소 2개의 단지가 필요해요. 단지 상세에서 ❤ 로 추가하세요.{" "}
          <Link className="text-blue-600 hover:underline" href="/favorites">
            즐겨찾기로
          </Link>
        </p>
      </div>
    );
  }

  // 각 단지 detail 병렬 fetch — 즐겨찾기 N개는 일반적으로 작음(<10).
  const details: ListingDetail[] = await Promise.all(
    visible.map(async (f) => {
      const r = await apiFetch(`/api/v1/listings/${f.id}`);
      return r.ok ? ((await r.json()) as ListingDetail) : ({ ...f, units: [] } as ListingDetail);
    }),
  );

  const rows: Array<{ label: string; render: (d: ListingDetail) => React.ReactNode }> = [
    {
      label: "유형",
      render: (d) => (
        <span className="rounded bg-blue-50 px-2 py-0.5 text-xs text-blue-700">
          {LISTING_TYPE_LABEL[d.listingType] ?? d.listingType}
        </span>
      ),
    },
    {
      label: "지역",
      render: (d) => (
        <span className="text-sm">
          {d.sido ?? "-"} {d.sigungu ?? ""}
        </span>
      ),
    },
    {
      label: "접수 마감",
      render: (d) => (
        <div>
          <div className={`text-sm ${dDayTone(d.applicationEnd)}`}>{dDay(d.applicationEnd) || "-"}</div>
          <div className="text-xs text-zinc-500">{formatDate(d.applicationEnd)}</div>
        </div>
      ),
    },
    {
      label: "당첨 발표",
      render: (d) => (
        <div>
          <div className={`text-sm ${dDayTone(d.winnerAnnouncementDate)}`}>
            {dDay(d.winnerAnnouncementDate) || "-"}
          </div>
          <div className="text-xs text-zinc-500">{formatDate(d.winnerAnnouncementDate)}</div>
        </div>
      ),
    },
    {
      label: "입주 예정",
      render: (d) => (
        <div>
          <div className={`text-sm ${dDayTone(d.moveInDate)}`}>{dDay(d.moveInDate) || "-"}</div>
          <div className="text-xs text-zinc-500">{formatDate(d.moveInDate)}</div>
        </div>
      ),
    },
    { label: "면적", render: (d) => <span className="text-sm">{sizeRange(d)}</span> },
    {
      label: "분양가 / 임대",
      render: (d) =>
        RENTAL_TYPES.has(d.listingType) ? (
          <div className="text-sm">
            <div>보증금 {depositRange(d)}</div>
            <div className="text-xs text-zinc-500">월 {rentRange(d)}</div>
          </div>
        ) : (
          <span className="text-sm">{priceRange(d)}</span>
        ),
    },
    {
      label: "평당가 (추정)",
      render: (d) =>
        RENTAL_TYPES.has(d.listingType) ? (
          <span className="text-xs text-zinc-400">-</span>
        ) : (
          <span className="text-sm text-zinc-600">{pyungPriceRange(d)}</span>
        ),
    },
    {
      label: "총 공급",
      render: (d) => <span className="text-sm">{d.totalSupply ?? "-"}세대</span>,
    },
    {
      label: "위치",
      render: (d) =>
        d.latitude != null && d.longitude != null ? (
          <span className="text-xs text-zinc-500">
            ({Number(d.latitude).toFixed(3)}, {Number(d.longitude).toFixed(3)})
          </span>
        ) : (
          <span className="text-xs text-zinc-400">미확보</span>
        ),
    },
  ];

  // exclude 토글 헬퍼: 현재 visible 의 id 를 클릭 시 ?exclude= 로 푸시.
  const buildExcludeHref = (idToExclude: number) => {
    const u = new URLSearchParams();
    for (const id of exclude) u.append("exclude", id);
    u.append("exclude", String(idToExclude));
    return `/favorites/compare?${u.toString()}`;
  };
  const buildIncludeHref = (idToInclude: string) => {
    const remaining = [...exclude].filter((id) => id !== idToInclude);
    const u = new URLSearchParams();
    for (const id of remaining) u.append("exclude", id);
    return remaining.length === 0 ? "/favorites/compare" : `/favorites/compare?${u.toString()}`;
  };
  const excludedItems = favorites.filter((f) => exclude.has(String(f.id)));

  return (
    <div className="space-y-4">
      <div className="flex items-end justify-between">
        <div>
          <h1 className="text-2xl font-bold">즐겨찾기 비교</h1>
          <p className="mt-1 text-sm text-zinc-500">
            가격 / 일정 / 위치를 옆에 두고 한눈에 비교하세요.
          </p>
        </div>
        <Link href="/favorites" className="text-sm text-zinc-500 hover:text-zinc-900">
          ← 즐겨찾기로
        </Link>
      </div>

      <div className="overflow-x-auto rounded-lg border border-zinc-200 bg-white">
        <table className="w-full text-sm">
          <thead className="bg-zinc-50">
            <tr>
              <th className="sticky left-0 z-10 w-32 bg-zinc-50 px-3 py-2 text-left text-xs text-zinc-500">
                항목
              </th>
              {details.map((d) => (
                <th key={d.id} className="min-w-[180px] px-3 py-3 text-left">
                  <div className="flex items-start justify-between gap-2">
                    <Link
                      href={`/listings/${d.id}`}
                      className="line-clamp-2 text-sm font-semibold leading-snug text-zinc-900 hover:text-blue-600"
                    >
                      {d.name}
                    </Link>
                    <Link
                      href={buildExcludeHref(d.id)}
                      className="shrink-0 rounded border border-zinc-200 px-2 py-0.5 text-xs text-zinc-400 hover:text-zinc-700"
                      title="비교에서 제외"
                    >
                      ×
                    </Link>
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.label} className="border-t border-zinc-100">
                <td className="sticky left-0 z-10 bg-white px-3 py-3 text-xs font-medium text-zinc-600">
                  {r.label}
                </td>
                {details.map((d) => (
                  <td key={d.id} className="px-3 py-3 align-top">
                    {r.render(d)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {excludedItems.length > 0 && (
        <div className="rounded border border-zinc-200 bg-zinc-50 p-3">
          <div className="text-xs font-medium text-zinc-600">비교에서 제외됨</div>
          <div className="mt-2 flex flex-wrap gap-2">
            {excludedItems.map((it) => (
              <Link
                key={it.id}
                href={buildIncludeHref(String(it.id))}
                className="rounded-full border border-zinc-300 bg-white px-3 py-1 text-xs text-zinc-700 hover:bg-zinc-100"
              >
                + {it.name}
              </Link>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
