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

export default async function HomePage() {
  const res = await apiFetch("/api/v1/listings?size=30&sort=CLOSING");
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
    </div>
  );
}
