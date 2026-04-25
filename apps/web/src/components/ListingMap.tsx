"use client";

import "leaflet/dist/leaflet.css";
import L from "leaflet";
import { useEffect, useMemo, useRef } from "react";
import { MapContainer, Marker, Popup, TileLayer, useMap } from "react-leaflet";

// Leaflet 기본 아이콘이 번들러와 호환 안 됨 → 명시적 PNG 지정
const defaultIcon = L.icon({
  iconUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png",
  iconRetinaUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png",
  shadowUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png",
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
});
L.Marker.prototype.options.icon = defaultIcon;

export interface MapPoint {
  id: number;
  lat: number;
  lng: number;
  title: string;
  subtitle?: string;
  href?: string;
  highlight?: boolean;
}

export default function ListingMap({
  points,
  className,
  selectedId,
  workplaces,
}: {
  points: MapPoint[];
  className?: string;
  selectedId?: number;
  workplaces?: Array<{ lat: number; lng: number; label: string }>;
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
    if (validPoints.length === 0) return [37.5665, 126.978]; // 서울시청 기본
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
        {validPoints.map((p) => (
          <Marker
            key={`l-${p.id}`}
            position={[p.lat, p.lng]}
            icon={p.id === selectedId || p.highlight ? highlightedIcon : defaultIcon}
          >
            <Popup>
              <div className="space-y-1">
                <strong>{p.title}</strong>
                {p.subtitle && <div className="text-xs text-zinc-500">{p.subtitle}</div>}
                {p.href && (
                  <a href={p.href} className="text-xs text-blue-600 hover:underline">
                    상세 보기 →
                  </a>
                )}
              </div>
            </Popup>
          </Marker>
        ))}
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

const highlightedIcon = L.divIcon({
  className: "leaflet-highlighted",
  html: `<div style="background:#dc2626;border:2px solid white;border-radius:50%;width:18px;height:18px;box-shadow:0 0 0 2px #dc2626;"></div>`,
  iconSize: [18, 18],
  iconAnchor: [9, 9],
});

const workplaceIcon = L.divIcon({
  className: "leaflet-workplace",
  html: `<div style="background:#0d9488;border:2px solid white;border-radius:4px;width:20px;height:20px;display:flex;align-items:center;justify-content:center;color:white;font-size:11px;line-height:1;">🏢</div>`,
  iconSize: [20, 20],
  iconAnchor: [10, 10],
});

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
      map.setView(all[0], 13);
    } else {
      const bounds = L.latLngBounds(all);
      map.fitBounds(bounds, { padding: [40, 40], maxZoom: 12 });
    }
  }, [points, workplaces, map]);
  return null;
}
