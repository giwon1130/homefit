import Link from "next/link";
import { apiFetch, type ListingPage } from "@/lib/api";

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
  const now = new Date();
  const end = new Date(iso);
  const diff = Math.ceil((end.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
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

export default async function HomePage() {
  const res = await apiFetch("/api/v1/listings?size=20&sort=CLOSING");
  if (!res.ok) {
    return (
      <div className="rounded border border-red-200 bg-red-50 p-4 text-red-700">
        청약 목록을 불러오지 못했습니다 (HTTP {res.status}).
      </div>
    );
  }
  const page: ListingPage = await res.json();

  return (
    <div className="space-y-4">
      <div className="flex items-end justify-between">
        <h1 className="text-2xl font-bold">진행 중인 청약</h1>
        <span className="text-sm text-zinc-500">총 {page.total}건</span>
      </div>

      <ul className="divide-y divide-zinc-200 overflow-hidden rounded-lg border border-zinc-200 bg-white">
        {page.content.map((l) => (
          <li key={l.id}>
            <Link href={`/listings/${l.id}`} className="block p-4 hover:bg-zinc-50">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="inline-block rounded bg-blue-50 px-2 py-0.5 text-xs text-blue-700">
                      {LISTING_TYPE_LABEL[l.listingType] ?? l.listingType}
                    </span>
                    {l.sido && (
                      <span className="text-xs text-zinc-500">
                        {l.sido} {l.sigungu ?? ""}
                      </span>
                    )}
                  </div>
                  <h2 className="mt-1 truncate font-semibold">{l.name}</h2>
                  <p className="mt-1 truncate text-xs text-zinc-500">{l.address}</p>
                </div>
                <div className="shrink-0 text-right text-xs">
                  <div className="font-semibold text-zinc-900">
                    {daysUntil(l.applicationEnd)}
                  </div>
                  <div className="text-zinc-500">
                    {formatDate(l.applicationStart)} ~ {formatDate(l.applicationEnd)}
                  </div>
                </div>
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
