import { notFound } from "next/navigation";
import Link from "next/link";
import {
  apiFetch,
  type EligibilityResp,
  type ListingDetail,
  type LoanEstimateResp,
} from "@/lib/api";
import { auth } from "@/auth";
import FavoriteButton from "@/components/FavoriteButton";
import ListingMap from "@/components/ListingMap";

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
    return new Date(iso).toLocaleString("ko-KR", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return iso;
  }
}

function formatDateOnly(iso?: string) {
  if (!iso) return "-";
  try {
    return new Date(iso).toLocaleDateString("ko-KR");
  } catch {
    return iso;
  }
}

function formatKrw(n?: number) {
  if (n == null) return "-";
  const eok = Math.floor(n / 100_000_000);
  const man = Math.floor((n % 100_000_000) / 10_000);
  if (eok > 0) return `${eok}억${man > 0 ? ` ${man.toLocaleString()}만원` : "원"}`;
  if (man > 0) return `${man.toLocaleString()}만원`;
  return `${n.toLocaleString()}원`;
}

/** "D-3" / "오늘" / "마감" 형태로 환산. iso 가 없으면 null. */
function dayDelta(iso?: string): { label: string; tone: "urgent" | "soon" | "normal" | "past" } | null {
  if (!iso) return null;
  const diffDays = Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000);
  if (diffDays < 0) return { label: "지남", tone: "past" };
  if (diffDays === 0) return { label: "오늘", tone: "urgent" };
  const tone = diffDays <= 3 ? "urgent" : diffDays <= 14 ? "soon" : "normal";
  return { label: `D-${diffDays}`, tone };
}

/** 1m² → 0.3025평. priceMaxKrw 와 sizeM2 모두 있으면 평당가 (만원 단위) 계산. */
function pyungPriceMan(price?: number, sizeM2?: number | string): number | null {
  if (price == null || sizeM2 == null) return null;
  const m2 = typeof sizeM2 === "string" ? Number(sizeM2) : sizeM2;
  if (!Number.isFinite(m2) || m2 <= 0) return null;
  const pyung = m2 * 0.3025;
  return Math.round(price / pyung / 10_000);
}

/** 임대 유형 — 분양가 대신 보증금/월세 위주로 표시. */
const RENTAL_TYPES = new Set([
  "HAPPY_HOUSE",
  "PURCHASE_RENTAL",
  "JEONSE_RENTAL",
  "NATIONAL_RENTAL",
]);

type Props = { params: Promise<{ id: string }> };

export default async function ListingDetailPage({ params }: Props) {
  const { id } = await params;
  const res = await apiFetch(`/api/v1/listings/${id}`);
  if (res.status === 404) notFound();
  if (!res.ok) {
    return <div className="text-red-600">로딩 실패: HTTP {res.status}</div>;
  }
  const listing: ListingDetail = await res.json();

  // Only fetch eligibility + favorite state if logged in.
  const session = await auth();
  let eligibility: EligibilityResp | null = null;
  let isFavorited = false;
  let loan: LoanEstimateResp | null = null;
  if (session?.accessToken) {
    const [er, fr, lr] = await Promise.all([
      apiFetch(`/api/v1/listings/${id}/eligibility`),
      apiFetch("/api/v1/favorites"),
      apiFetch(`/api/v1/listings/${id}/loan-estimate`, { allow401: true }),
    ]);
    if (er.ok) eligibility = await er.json();
    if (fr.ok) {
      const list = (await fr.json()) as { id: number }[];
      isFavorited = list.some((x) => x.id === Number(id));
    }
    if (lr.ok) loan = await lr.json();
  }

  return (
    <div className="space-y-6">
      <div>
        <Link href="/" className="text-sm text-zinc-500 hover:text-zinc-900">
          ← 목록
        </Link>
      </div>

      <header className="space-y-2">
        <div className="flex items-center gap-2">
          <span className="inline-block rounded bg-blue-50 px-2 py-0.5 text-xs text-blue-700">
            {LISTING_TYPE_LABEL[listing.listingType] ?? listing.listingType}
          </span>
          {listing.sido && (
            <span className="text-xs text-zinc-500">
              {listing.sido} {listing.sigungu ?? ""}
            </span>
          )}
        </div>
        <div className="flex items-start justify-between gap-3">
          <h1 className="text-2xl font-bold">{listing.name}</h1>
          <FavoriteButton
            listingId={listing.id}
            initialFavorited={isFavorited}
            authed={!!session?.accessToken}
          />
        </div>
        <p className="text-sm text-zinc-500">{listing.address}</p>
        {listing.developer && (
          <p className="text-sm text-zinc-500">시행/시공: {listing.developer}</p>
        )}
      </header>

      <DDayStrip listing={listing} />
      <SummaryCard listing={listing} />

      {listing.latitude != null && listing.longitude != null && (
        <section>
          <h2 className="mb-2 font-semibold">위치</h2>
          <ListingMap
            points={[
              {
                id: listing.id,
                lat: listing.latitude,
                lng: listing.longitude,
                title: listing.name,
                subtitle: listing.address ?? undefined,
                highlight: true,
              },
            ]}
            polygons={
              listing.polygonGeoJson
                ? [{ id: listing.id, geojson: listing.polygonGeoJson, color: "#dc2626" }]
                : undefined
            }
            className="h-[360px]"
          />
        </section>
      )}

      {eligibility && (
        <section className="rounded-lg border border-blue-200 bg-blue-50 p-4">
          <h2 className="mb-3 font-semibold">내 자격 판정</h2>
          {eligibility.bestSupplyType ? (
            <p className="mb-3 text-sm">
              <strong>추천 유형:</strong>{" "}
              {SUPPLY_TYPE_LABEL[eligibility.bestSupplyType] ?? eligibility.bestSupplyType}
            </p>
          ) : (
            <p className="mb-3 text-sm text-zinc-600">가능한 유형이 없습니다.</p>
          )}
          <ul className="space-y-2">
            {eligibility.details.map((d) => (
              <li
                key={d.supplyType}
                className={
                  d.eligible
                    ? "rounded bg-white p-3"
                    : "rounded bg-white p-3 opacity-60"
                }
              >
                <div className="flex items-center justify-between">
                  <span className="font-medium">
                    {SUPPLY_TYPE_LABEL[d.supplyType] ?? d.supplyType}
                  </span>
                  <span
                    className={
                      d.eligible ? "text-sm text-green-700" : "text-sm text-zinc-500"
                    }
                  >
                    {d.eligible ? "가능" : "불가"}
                  </span>
                </div>
                <ul className="mt-1 list-disc pl-5 text-xs text-zinc-600">
                  {d.reasons.map((r, i) => (
                    <li key={i}>{r}</li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="grid grid-cols-2 gap-4 text-sm">
        <Field label="접수 시작" value={formatDate(listing.applicationStart)} />
        <Field label="접수 마감" value={formatDate(listing.applicationEnd)} />
        <Field label="모집공고일" value={formatDateOnly(listing.announcementDate)} />
        <Field label="당첨자 발표" value={formatDateOnly(listing.winnerAnnouncementDate)} />
        <Field label="입주예정" value={formatDateOnly(listing.moveInDate)} />
        <Field label="총 공급세대" value={listing.totalSupply?.toString() ?? "-"} />
      </section>

      {loan && <LoanCard loan={loan} />}

      {listing.units.length > 0 && (
        <UnitsTable listing={listing} />
      )}

      {listing.documentUrl && (
        <a
          href={listing.documentUrl}
          target="_blank"
          rel="noreferrer"
          className="inline-block rounded border border-zinc-300 px-4 py-2 text-sm hover:bg-zinc-50"
        >
          원문 공고 보기 →
        </a>
      )}
    </div>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded border border-zinc-200 bg-white p-3">
      <div className="text-xs text-zinc-500">{label}</div>
      <div className="mt-1 font-medium">{value}</div>
    </div>
  );
}

function DDayStrip({ listing }: { listing: ListingDetail }) {
  const items: Array<{
    label: string;
    iso?: string;
    delta: ReturnType<typeof dayDelta>;
  }> = [
    { label: "접수 마감", iso: listing.applicationEnd, delta: dayDelta(listing.applicationEnd) },
    { label: "당첨자 발표", iso: listing.winnerAnnouncementDate, delta: dayDelta(listing.winnerAnnouncementDate) },
    { label: "입주예정", iso: listing.moveInDate, delta: dayDelta(listing.moveInDate) },
  ];
  // 적어도 하나 D-day 가 살아있는 게 있어야 의미 있음.
  const hasAny = items.some((i) => i.delta != null);
  if (!hasAny) return null;
  return (
    <section className="grid grid-cols-3 gap-2">
      {items.map((it) => {
        const tone = it.delta?.tone ?? "past";
        const wrapClass =
          tone === "urgent"
            ? "rounded-lg border border-red-200 bg-red-50 p-3"
            : tone === "soon"
              ? "rounded-lg border border-amber-200 bg-amber-50 p-3"
              : tone === "past"
                ? "rounded-lg border border-zinc-200 bg-zinc-50 p-3 opacity-60"
                : "rounded-lg border border-zinc-200 bg-white p-3";
        const labelClass =
          tone === "urgent"
            ? "text-2xl font-bold text-red-700"
            : tone === "soon"
              ? "text-2xl font-bold text-amber-700"
              : tone === "past"
                ? "text-2xl font-bold text-zinc-400"
                : "text-2xl font-bold text-zinc-700";
        return (
          <div key={it.label} className={wrapClass}>
            <div className="text-xs text-zinc-500">{it.label}</div>
            <div className={labelClass}>{it.delta?.label ?? "-"}</div>
            <div className="text-xs text-zinc-500">{formatDateOnly(it.iso)}</div>
          </div>
        );
      })}
    </section>
  );
}

function SummaryCard({ listing }: { listing: ListingDetail }) {
  if (listing.units.length === 0) return null;
  const isRental = RENTAL_TYPES.has(listing.listingType);
  const sizes = listing.units.map((u) => Number(u.sizeM2)).filter((n) => Number.isFinite(n) && n > 0);
  const minSize = sizes.length > 0 ? Math.min(...sizes) : null;
  const maxSize = sizes.length > 0 ? Math.max(...sizes) : null;

  if (isRental) {
    const deposits = listing.units.map((u) => u.depositAmount).filter((n): n is number => n != null && n > 0);
    const monthly = listing.units.map((u) => u.monthlyRent).filter((n): n is number => n != null && n > 0);
    return (
      <section className="grid gap-2 sm:grid-cols-3">
        <Stat
          label="면적 범위"
          value={
            minSize != null
              ? minSize === maxSize
                ? `${minSize.toFixed(1)}㎡`
                : `${minSize.toFixed(1)} ~ ${maxSize?.toFixed(1)}㎡`
              : "-"
          }
        />
        <Stat
          label="보증금"
          value={
            deposits.length > 0
              ? Math.min(...deposits) === Math.max(...deposits)
                ? formatKrw(Math.min(...deposits))
                : `${formatKrw(Math.min(...deposits))} ~ ${formatKrw(Math.max(...deposits))}`
              : "-"
          }
        />
        <Stat
          label="월 임대료"
          value={
            monthly.length > 0
              ? Math.min(...monthly) === Math.max(...monthly)
                ? formatKrw(Math.min(...monthly))
                : `${formatKrw(Math.min(...monthly))} ~ ${formatKrw(Math.max(...monthly))}`
              : "-"
          }
        />
      </section>
    );
  }

  const prices = listing.units.map((u) => u.priceMaxKrw).filter((n): n is number => n != null && n > 0);
  const minPrice = prices.length > 0 ? Math.min(...prices) : null;
  const maxPrice = prices.length > 0 ? Math.max(...prices) : null;
  // 평당가 범위 (대표값) — 각 unit 별 평당가의 min/max
  const pyungPrices = listing.units
    .map((u) => pyungPriceMan(u.priceMaxKrw, u.sizeM2))
    .filter((n): n is number => n != null && n > 0);
  const minPp = pyungPrices.length > 0 ? Math.min(...pyungPrices) : null;
  const maxPp = pyungPrices.length > 0 ? Math.max(...pyungPrices) : null;

  return (
    <section className="grid gap-2 sm:grid-cols-3">
      <Stat
        label="면적 범위"
        value={
          minSize != null
            ? minSize === maxSize
              ? `${minSize.toFixed(1)}㎡`
              : `${minSize.toFixed(1)} ~ ${maxSize?.toFixed(1)}㎡`
            : "-"
        }
      />
      <Stat
        label="분양가 범위"
        value={
          minPrice != null
            ? minPrice === maxPrice
              ? formatKrw(minPrice)
              : `${formatKrw(minPrice)} ~ ${formatKrw(maxPrice ?? minPrice)}`
            : "-"
        }
      />
      <Stat
        label="평당가 (추정)"
        value={
          minPp != null
            ? minPp === maxPp
              ? `${minPp.toLocaleString()}만원`
              : `${minPp.toLocaleString()} ~ ${maxPp?.toLocaleString()}만원`
            : "-"
        }
      />
    </section>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-zinc-200 bg-white p-3">
      <div className="text-xs text-zinc-500">{label}</div>
      <div className="mt-1 text-base font-semibold">{value}</div>
    </div>
  );
}

function UnitsTable({ listing }: { listing: ListingDetail }) {
  const isRental = RENTAL_TYPES.has(listing.listingType);
  return (
    <section>
      <h2 className="mb-2 font-semibold">공급 유형별</h2>
      <div className="overflow-x-auto rounded-lg border border-zinc-200">
        <table className="w-full text-sm">
          <thead className="bg-zinc-50 text-xs text-zinc-500">
            <tr>
              <th className="px-3 py-2 text-left">주택형</th>
              <th className="px-3 py-2 text-right">면적</th>
              <th className="px-3 py-2 text-right">공급세대</th>
              {isRental ? (
                <>
                  <th className="px-3 py-2 text-right">보증금</th>
                  <th className="px-3 py-2 text-right">월 임대료</th>
                </>
              ) : (
                <>
                  <th className="px-3 py-2 text-right">최고분양가</th>
                  <th className="px-3 py-2 text-right">평당가 (추정)</th>
                </>
              )}
            </tr>
          </thead>
          <tbody>
            {listing.units.map((u) => {
              const pp = pyungPriceMan(u.priceMaxKrw, u.sizeM2);
              return (
                <tr key={u.id} className="border-t border-zinc-100">
                  <td className="px-3 py-2">{u.unitType ?? "-"}</td>
                  <td className="px-3 py-2 text-right">
                    {u.sizeM2 ? `${Number(u.sizeM2).toFixed(1)}㎡` : "-"}
                  </td>
                  <td className="px-3 py-2 text-right">{u.supplyCount ?? "-"}</td>
                  {isRental ? (
                    <>
                      <td className="px-3 py-2 text-right">{formatKrw(u.depositAmount)}</td>
                      <td className="px-3 py-2 text-right">{formatKrw(u.monthlyRent)}</td>
                    </>
                  ) : (
                    <>
                      <td className="px-3 py-2 text-right">{formatKrw(u.priceMaxKrw)}</td>
                      <td className="px-3 py-2 text-right text-zinc-500">
                        {pp != null ? `${pp.toLocaleString()}만원` : "-"}
                      </td>
                    </>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {!isRental && (
        <p className="mt-1 text-xs text-zinc-500">
          평당가 = 최고분양가 ÷ (전용면적 × 0.3025) — 공급면적 기준이 아니므로 실제 평당가와 차이 가능.
        </p>
      )}
    </section>
  );
}

function LoanCard({ loan }: { loan: LoanEstimateResp }) {
  return (
    <section className="rounded-lg border border-amber-200 bg-amber-50 p-4">
      <div className="flex items-baseline justify-between">
        <h2 className="font-semibold">내 대출 가능액 (추정)</h2>
        <span className="text-xs text-zinc-500">2025 기준 · 실제 한도는 기관 시뮬레이터로 확인</span>
      </div>

      {loan.recommended ? (
        <div className="mt-3 rounded bg-white p-3">
          <div className="text-xs text-zinc-500">추천 상품</div>
          <div className="mt-1 flex items-baseline gap-2">
            <span className="font-semibold text-amber-900">{loan.recommended.name}</span>
            <span className="text-lg font-bold">
              {loan.recommended.limitKrw ? formatKrw(loan.recommended.limitKrw) : "-"}
            </span>
          </div>
          {loan.selfFundingKrw != null && (
            <div className="mt-1 text-xs text-zinc-600">
              자기부담 약 <strong>{formatKrw(loan.selfFundingKrw)}</strong>
              {" "}
              (분양가 {formatKrw(loan.listingPriceKrw)} 기준)
            </div>
          )}
        </div>
      ) : (
        <p className="mt-3 text-sm text-zinc-600">적용 가능한 상품이 없어요. 프로필 소득을 확인해주세요.</p>
      )}

      <ul className="mt-3 space-y-2">
        {loan.products.map((p) => (
          <li
            key={p.name}
            className={p.eligible ? "rounded bg-white p-3" : "rounded bg-white p-3 opacity-60"}
          >
            <div className="flex items-center justify-between">
              <span className="font-medium">{p.name}</span>
              <span className={p.eligible ? "text-sm text-green-700" : "text-sm text-zinc-500"}>
                {p.eligible
                  ? p.limitKrw
                    ? `${formatKrw(p.limitKrw)} 가능`
                    : "가능"
                  : "불가"}
              </span>
            </div>
            <ul className="mt-1 list-disc pl-5 text-xs text-zinc-600">
              {p.reasons.map((r, i) => (
                <li key={i}>{r}</li>
              ))}
            </ul>
          </li>
        ))}
      </ul>

      <p className="mt-3 text-xs text-zinc-500">
        ⚠ 여기 숫자는 단순 추정. 실제 한도는 기관별 심사·DSR·신용도에 따라 달라집니다.
        <br />
        정확한 시뮬레이션:{" "}
        <a
          href="https://nhuf.molit.go.kr/"
          target="_blank"
          rel="noreferrer"
          className="text-blue-600 hover:underline"
        >
          기금e든든
        </a>
        {", "}
        <a
          href="https://www.hf.go.kr/"
          target="_blank"
          rel="noreferrer"
          className="text-blue-600 hover:underline"
        >
          한국주택금융공사
        </a>
      </p>
    </section>
  );
}
