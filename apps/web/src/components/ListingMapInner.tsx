"use client";

import "leaflet/dist/leaflet.css";
import L from "leaflet";
import { useEffect, useMemo, useRef } from "react";
import { Circle, GeoJSON, MapContainer, Marker, Popup, TileLayer, useMap } from "react-leaflet";
import type { FeatureCollection } from "geojson";

// 직장 마커용 div 아이콘
const workplaceIcon = L.divIcon({
  className: "leaflet-workplace",
  html: `<div style="background:#0d9488;border:2px solid white;border-radius:4px;width:22px;height:22px;display:flex;align-items:center;justify-content:center;color:white;font-size:12px;line-height:1;box-shadow:0 1px 4px rgba(0,0,0,.3);">🏢</div>`,
  iconSize: [22, 22],
  iconAnchor: [11, 11],
});

export interface MapPoint {
  id: number;
  lat: number;
  lng: number;
  title: string;
  subtitle?: string;
  href?: string;
  /** 0~100 점수 — 색상 결정에 사용 */
  score?: number;
  /** 단지 규모 — 반경 가중치. 없으면 기본 80m */
  totalSupply?: number;
  highlight?: boolean;
}

/**
 * 점수에 따른 색상 (낮을수록 회색, 높을수록 진한 파랑/녹색).
 */
function colorForScore(score?: number): { fill: string; stroke: string } {
  if (score == null) return { fill: "#94a3b8", stroke: "#475569" };
  if (score >= 70) return { fill: "#10b981", stroke: "#047857" }; // emerald
  if (score >= 50) return { fill: "#3b82f6", stroke: "#1d4ed8" }; // blue
  if (score >= 30) return { fill: "#f59e0b", stroke: "#b45309" }; // amber
  return { fill: "#94a3b8", stroke: "#475569" }; // slate
}

/** 단지 규모에 따른 반경(m). 부재 시 80m. */
function radiusFor(supply?: number): number {
  if (!supply) return 80;
  if (supply > 2000) return 200;
  if (supply > 1000) return 160;
  if (supply > 500) return 130;
  if (supply > 200) return 100;
  return 70;
}

export default function ListingMap({
  points,
  className,
  selectedId,
  workplaces,
  polygons,
}: {
  points: MapPoint[];
  className?: string;
  selectedId?: number;
  workplaces?: Array<{ lat: number; lng: number; label: string }>;
  /** 청약 단지 폴리곤. 있으면 원형 마커 대신 폴리곤 우선 표시. */
  polygons?: Array<{ id: number; geojson: FeatureCollection; color?: string }>;
}) {
  const validPoints = useMemo(
    () => points.filter((p) => Number.isFinite(p.lat) && Number.isFinite(p.lng)),
    [points],
  );
  const validWorkplaces = useMemo(
    () => (workplaces ?? []).filter((w) => Number.isFinite(w.lat) && Number.isFinite(w.lng)),
    [workplaces],
  );

  const center: [number, number] = useMemo(() => {
    if (validPoints.length === 0) return [37.5665, 126.978];
    const lat = validPoints.reduce((s, p) => s + p.lat, 0) / validPoints.length;
    const lng = validPoints.reduce((s, p) => s + p.lng, 0) / validPoints.length;
    return [lat, lng];
  }, [validPoints]);

  if (validPoints.length === 0 && validWorkplaces.length === 0) {
    return (
      <div
        className={`flex h-full min-h-[300px] items-center justify-center rounded-lg border border-zinc-200 bg-zinc-50 text-sm text-zinc-500 ${className ?? ""}`}
      >
        지도에 표시할 좌표가 없어요
      </div>
    );
  }

  return (
    <div className={`overflow-hidden rounded-lg border border-zinc-200 ${className ?? ""}`}>
      <MapContainer
        center={center}
        zoom={validPoints.length > 1 ? 7 : 14}
        style={{ height: "100%", width: "100%", minHeight: 300 }}
        scrollWheelZoom
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <FitBounds points={validPoints} workplaces={validWorkplaces} />
        {(polygons ?? []).map((poly) => (
          <GeoJSON
            key={`poly-${poly.id}`}
            data={poly.geojson}
            style={{
              color: poly.color ?? "#dc2626",
              weight: 2,
              fillColor: poly.color ?? "#dc2626",
              fillOpacity: 0.25,
            }}
          />
        ))}
        {validPoints
          .filter((p) => !(polygons ?? []).some((g) => g.id === p.id))
          .map((p) => {
          const isHi = p.id === selectedId || p.highlight;
          const c = colorForScore(p.score);
          return (
            <Circle
              key={`l-${p.id}`}
              center={[p.lat, p.lng]}
              radius={radiusFor(p.totalSupply)}
              pathOptions={{
                fillColor: c.fill,
                color: c.stroke,
                fillOpacity: isHi ? 0.55 : 0.35,
                weight: isHi ? 3 : 1.5,
              }}
            >
              <Popup>
                <div className="space-y-1">
                  <strong>{p.title}</strong>
                  {p.subtitle && <div className="text-xs text-zinc-500">{p.subtitle}</div>}
                  {p.score != null && (
                    <div className="text-xs">
                      매칭 점수: <span className="font-semibold">{p.score}</span> / 100
                    </div>
                  )}
                  {p.href && (
                    <a href={p.href} className="text-xs text-blue-600 hover:underline">
                      상세 보기 →
                    </a>
                  )}
                </div>
              </Popup>
            </Circle>
          );
        })}
        {validWorkplaces.map((w, i) => (
          <Marker key={`w-${i}`} position={[w.lat, w.lng]} icon={workplaceIcon}>
            <Popup>
              <strong>🏢 {w.label}</strong>
            </Popup>
          </Marker>
        ))}
      </MapContainer>
    </div>
  );
}

function FitBounds({
  points,
  workplaces,
}: {
  points: MapPoint[];
  workplaces: Array<{ lat: number; lng: number }>;
}) {
  const map = useMap();
  const lastKey = useRef("");
  useEffect(() => {
    const all: Array<[number, number]> = [
      ...points.map((p) => [p.lat, p.lng] as [number, number]),
      ...workplaces.map((w) => [w.lat, w.lng] as [number, number]),
    ];
    if (all.length === 0) return;
    const key = all.map((p) => p.join(",")).join("|");
    if (key === lastKey.current) return;
    lastKey.current = key;
    if (all.length === 1) {
      map.setView(all[0], 14);
    } else {
      const bounds = L.latLngBounds(all);
      map.fitBounds(bounds, { padding: [40, 40], maxZoom: 12 });
    }
  }, [points, workplaces, map]);
  return null;
}
