"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import type { FeatureCollection } from "geojson";
import ListingMap, { type MapPoint } from "@/components/ListingMap";
import type { MatchedListing } from "@/lib/api";

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

export default function MatchInteractive({
  matches,
  workplaces,
  polygons,
}: {
  matches: MatchedListing[];
  workplaces: Array<{ lat: number; lng: number; label: string }>;
  polygons: Array<{ id: number; geojson: FeatureCollection; color?: string }>;
}) {
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const cardRefs = useRef<Map<number, HTMLLIElement>>(new Map());

  const points: MapPoint[] = matches
    .filter((m) => m.listing.latitude != null && m.listing.longitude != null)
    .map(({ listing: l, score }) => ({
      id: l.id,
      lat: l.latitude as number,
      lng: l.longitude as number,
      title: l.name,
      subtitle: `${l.sido ?? ""} ${l.sigungu ?? ""}`,
      href: `/listings/${l.id}`,
      score: score.total,
      totalSupply: l.totalSupply,
    }));

  const handleMapClick = useCallback((id: number) => {
    setSelectedId(id);
    const el = cardRefs.current.get(id);
    if (el) el.scrollIntoView({ behavior: "smooth", block: "center" });
  }, []);

  // selectedId 가 사용자의 카드 hover 또는 마커 hover 로 바뀔 때 visual sync 만 수행.
  useEffect(() => {
    // no-op: 상태 변경은 자동으로 prop 으로 전달됨
  }, [selectedId]);

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_minmax(0,420px)]">
      <div>
        {matches.length === 0 ? (
          <div className="rounded border border-zinc-200 bg-white p-8 text-center text-zinc-500">
            매칭되는 청약이 없습니다.{" "}
            <Link href="/profile" className="text-blue-600 hover:underline">
              프로필
            </Link>
            을 채워보세요.
          </div>
        ) : (
          <ul className="space-y-3">
            {matches.map(({ listing: l, score }) => {
              const isHi = selectedId === l.id;
              return (
                <li
                  key={l.id}
                  ref={(el) => {
                    if (el) cardRefs.current.set(l.id, el);
                    else cardRefs.current.delete(l.id);
                  }}
                  onMouseEnter={() => setSelectedId(l.id)}
                  onMouseLeave={() =>
                    setSelectedId((prev) => (prev === l.id ? null : prev))
                  }
                >
                  <Link
                    href={`/listings/${l.id}`}
                    className={`block rounded-lg border bg-white p-4 transition ${
                      isHi
                        ? "border-blue-500 shadow-md ring-1 ring-blue-200"
                        : "border-zinc-200 hover:border-blue-400 hover:shadow-sm"
                    }`}
                  >
                    <div className="flex items-start gap-4">
                      <ScoreBadge total={score.total} max={score.max} />
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2 text-xs">
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
                        <div className="mt-1 grid grid-cols-2 gap-2 text-xs text-zinc-500 sm:grid-cols-4">
                          <Bar label="자격" v={score.eligibility} max={25} />
                          <Bar label="예산" v={score.budget} max={25} />
                          <Bar label="지역" v={score.region} max={20} />
                          <Bar
                            label={
                              score.commuteMinutes != null
                                ? `통근 ${score.commuteMinutes}분`
                                : "통근"
                            }
                            v={score.commute}
                            max={30}
                          />
                        </div>
                        <div className="mt-2 text-xs text-zinc-500">
                          접수 {formatDate(l.applicationStart)} ~{" "}
                          {formatDate(l.applicationEnd)} ({daysUntil(l.applicationEnd)})
                        </div>
                      </div>
                    </div>
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      <aside className="lg:sticky lg:top-6 lg:h-[calc(100vh-7rem)]">
        <ListingMap
          points={points}
          workplaces={workplaces}
          polygons={polygons}
          selectedId={selectedId}
          onPointHover={setSelectedId}
          onPointClick={handleMapClick}
          className="h-[400px] lg:h-full"
        />
        <div className="mt-2 space-y-1 text-xs text-zinc-500">
          <p>
            카드/마커에 마우스를 올리면 양쪽이 함께 강조됩니다. 마커 클릭 시 카드로 스크롤.
          </p>
          <div className="flex flex-wrap gap-2 text-[10px]">
            <span className="inline-flex items-center gap-1">
              <span className="inline-block h-3 w-3 rounded-full bg-emerald-500/40 ring-2 ring-emerald-700" />
              70+
            </span>
            <span className="inline-flex items-center gap-1">
              <span className="inline-block h-3 w-3 rounded-full bg-blue-500/40 ring-2 ring-blue-700" />
              50~69
            </span>
            <span className="inline-flex items-center gap-1">
              <span className="inline-block h-3 w-3 rounded-full bg-amber-500/40 ring-2 ring-amber-700" />
              30~49
            </span>
            <span className="inline-flex items-center gap-1">
              <span className="inline-block h-3 w-3 rounded-full bg-slate-400/40 ring-2 ring-slate-600" />
              ~29
            </span>
            <span className="inline-flex items-center gap-1">🏢 직장</span>
          </div>
        </div>
      </aside>
    </div>
  );
}

function ScoreBadge({ total, max }: { total: number; max: number }) {
  const ratio = max ? total / max : 0;
  const color = ratio >= 0.7 ? "bg-emerald-600" : ratio >= 0.4 ? "bg-blue-600" : "bg-zinc-400";
  return (
    <div
      className={`flex h-14 w-14 shrink-0 flex-col items-center justify-center rounded-full text-white ${color}`}
    >
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
