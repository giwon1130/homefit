"use client";

import dynamic from "next/dynamic";
import type { ComponentProps } from "react";
import type ListingMapInner from "./ListingMapInner";

// Leaflet 은 module-load 시점에 window 를 참조해 SSR 에서 깨짐.
// 클라이언트 전용 dynamic import 로 우회.
const ListingMapDynamic = dynamic(() => import("./ListingMapInner"), {
  ssr: false,
  loading: () => (
    <div className="flex h-full min-h-[300px] items-center justify-center rounded-lg border border-zinc-200 bg-zinc-50 text-sm text-zinc-500">
      지도를 불러오는 중…
    </div>
  ),
});

export type { MapPoint } from "./ListingMapInner";

export default function ListingMap(props: ComponentProps<typeof ListingMapInner>) {
  return <ListingMapDynamic {...props} />;
}
