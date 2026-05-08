import { redirect } from "next/navigation";
import Link from "next/link";
import { auth } from "@/auth";
import { apiFetch, type ListingSummary } from "@/lib/api";

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

export default async function FavoritesPage() {
  const session = await auth();
  if (!session?.accessToken) redirect("/login");

  const res = await apiFetch("/api/v1/favorites");
  if (!res.ok) {
    return (
      <div className="rounded border border-red-200 bg-red-50 p-4 text-red-700">
        즐겨찾기 로드 실패 (HTTP {res.status})
      </div>
    );
  }
  const items: ListingSummary[] = await res.json();

  return (
    <div className="space-y-4">
      <div className="flex items-end justify-between">
        <h1 className="text-2xl font-bold">즐겨찾기</h1>
        {items.length >= 2 && (
          <Link
            href="/favorites/compare"
            className="rounded border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-700 hover:bg-zinc-50"
          >
            비교하기 →
          </Link>
        )}
      </div>
      {items.length === 0 ? (
        <div className="rounded border border-zinc-200 bg-white p-8 text-center text-zinc-500">
          아직 즐겨찾기한 청약이 없어요. 상세 화면의 ❤ 버튼으로 추가하세요.
        </div>
      ) : (
        <ul className="divide-y divide-zinc-200 overflow-hidden rounded-lg border border-zinc-200 bg-white">
          {items.map((l) => (
            <li key={l.id}>
              <Link href={`/listings/${l.id}`} className="block p-4 hover:bg-zinc-50">
                <div className="flex items-center gap-2 text-xs">
                  <span className="rounded bg-blue-50 px-2 py-0.5 text-blue-700">
                    {LISTING_TYPE_LABEL[l.listingType] ?? l.listingType}
                  </span>
                  {l.sido && (
                    <span className="text-zinc-500">
                      {l.sido} {l.sigungu}
                    </span>
                  )}
                </div>
                <h2 className="mt-1 font-semibold">{l.name}</h2>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
