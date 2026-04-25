import { notFound } from "next/navigation";
import Link from "next/link";
import { apiFetch, type EligibilityResp, type ListingDetail } from "@/lib/api";
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
  if (session?.accessToken) {
    const [er, fr] = await Promise.all([
      apiFetch(`/api/v1/listings/${id}/eligibility`),
      apiFetch("/api/v1/favorites"),
    ]);
    if (er.ok) eligibility = await er.json();
    if (fr.ok) {
      const list = (await fr.json()) as { id: number }[];
      isFavorited = list.some((x) => x.id === Number(id));
    }
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
            className="h-[320px]"
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

      {listing.units.length > 0 && (
        <section>
          <h2 className="mb-2 font-semibold">공급 유형별</h2>
          <div className="overflow-hidden rounded-lg border border-zinc-200">
            <table className="w-full text-sm">
              <thead className="bg-zinc-50 text-xs text-zinc-500">
                <tr>
                  <th className="px-3 py-2 text-left">주택형</th>
                  <th className="px-3 py-2 text-right">면적</th>
                  <th className="px-3 py-2 text-right">공급세대</th>
                  <th className="px-3 py-2 text-right">최고분양가</th>
                </tr>
              </thead>
              <tbody>
                {listing.units.map((u) => (
                  <tr key={u.id} className="border-t border-zinc-100">
                    <td className="px-3 py-2">{u.unitType ?? "-"}</td>
                    <td className="px-3 py-2 text-right">
                      {u.sizeM2 ? `${u.sizeM2}㎡` : "-"}
                    </td>
                    <td className="px-3 py-2 text-right">{u.supplyCount ?? "-"}</td>
                    <td className="px-3 py-2 text-right">{formatKrw(u.priceMaxKrw)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
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
