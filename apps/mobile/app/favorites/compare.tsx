import { useCallback, useState } from "react";
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { useFocusEffect, useRouter } from "expo-router";
import {
  apiFetch,
  LISTING_TYPE_LABEL,
  type ListingDetail,
  type ListingSummary,
  type ListingType,
} from "@/lib/api";

const RENTAL_TYPES = new Set<ListingType>([
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

function dDay(iso?: string): string {
  if (!iso) return "-";
  const d = Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000);
  if (d < 0) return "지남";
  if (d === 0) return "오늘";
  return `D-${d}`;
}

function dDayColor(iso?: string): string {
  if (!iso) return "#a1a1aa";
  const d = Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000);
  if (d < 0) return "#a1a1aa";
  if (d <= 3) return "#b91c1c";
  if (d <= 14) return "#b45309";
  return "#0a0a0a";
}

function formatKrw(n?: number | null): string {
  if (n == null || n <= 0) return "-";
  const eok = Math.floor(n / 100_000_000);
  const man = Math.floor((n % 100_000_000) / 10_000);
  if (eok > 0) return `${eok}억${man > 0 ? ` ${man.toLocaleString()}만` : ""}`;
  if (man > 0) return `${man.toLocaleString()}만`;
  return `${n.toLocaleString()}원`;
}

function priceRange(d: ListingDetail): string {
  const v = d.units.map((u) => u.priceMaxKrw).filter((n): n is number => n != null && n > 0);
  if (v.length === 0) return "-";
  const min = Math.min(...v);
  const max = Math.max(...v);
  return min === max ? formatKrw(min) : `${formatKrw(min)} ~ ${formatKrw(max)}`;
}

function depositRange(d: ListingDetail): string {
  const v = d.units.map((u) => u.depositAmount).filter((n): n is number => n != null && n > 0);
  if (v.length === 0) return "-";
  return formatKrw(Math.min(...v)) + (Math.min(...v) === Math.max(...v) ? "" : ` ~ ${formatKrw(Math.max(...v))}`);
}

function rentRange(d: ListingDetail): string {
  const v = d.units.map((u) => u.monthlyRent).filter((n): n is number => n != null && n > 0);
  if (v.length === 0) return "-";
  return formatKrw(Math.min(...v)) + (Math.min(...v) === Math.max(...v) ? "" : ` ~ ${formatKrw(Math.max(...v))}`);
}

function sizeRange(d: ListingDetail): string {
  const v = d.units
    .map((u) => (u.sizeM2 != null ? Number(u.sizeM2) : null))
    .filter((n): n is number => n != null && n > 0);
  if (v.length === 0) return "-";
  const min = Math.min(...v);
  const max = Math.max(...v);
  return min === max ? `${min.toFixed(1)}㎡` : `${min.toFixed(1)} ~ ${max.toFixed(1)}㎡`;
}

function pyungPriceRange(d: ListingDetail): string {
  const pps: number[] = [];
  for (const u of d.units) {
    if (u.priceMaxKrw == null || u.sizeM2 == null) continue;
    const m2 = Number(u.sizeM2);
    if (!Number.isFinite(m2) || m2 <= 0) continue;
    pps.push(Math.round(u.priceMaxKrw / (m2 * 0.3025) / 10_000));
  }
  if (pps.length === 0) return "-";
  const min = Math.min(...pps);
  const max = Math.max(...pps);
  return min === max ? `${min.toLocaleString()}만` : `${min.toLocaleString()} ~ ${max.toLocaleString()}만`;
}

export default function CompareScreen() {
  const router = useRouter();
  const [excluded, setExcluded] = useState<Set<number>>(new Set());
  const [details, setDetails] = useState<ListingDetail[] | null>(null);
  const [favorites, setFavorites] = useState<ListingSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const r = await apiFetch("/api/v1/favorites");
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      const favs = (await r.json()) as ListingSummary[];
      setFavorites(favs);
      const detailRes = await Promise.all(
        favs.map(async (f) => {
          const dr = await apiFetch(`/api/v1/listings/${f.id}`);
          return dr.ok ? ((await dr.json()) as ListingDetail) : null;
        }),
      );
      setDetails(detailRes.filter((d): d is ListingDetail => d != null));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  if (loading || details == null) {
    return (
      <View style={[styles.container, styles.center]}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  if (error) {
    return (
      <View style={[styles.container, styles.center]}>
        <Text style={styles.muted}>{error}</Text>
      </View>
    );
  }

  const visible = details.filter((d) => !excluded.has(d.id));
  if (visible.length < 2) {
    return (
      <View style={[styles.container, styles.center]}>
        <Text style={styles.title}>비교 단지가 부족해요</Text>
        <Text style={styles.muted}>최소 2개 즐겨찾기가 필요합니다.</Text>
        {excluded.size > 0 && (
          <Pressable onPress={() => setExcluded(new Set())} style={styles.btn}>
            <Text style={styles.btnText}>모두 다시 비교</Text>
          </Pressable>
        )}
      </View>
    );
  }

  const rows: Array<{ label: string; render: (d: ListingDetail) => React.ReactNode }> = [
    {
      label: "유형",
      render: (d) => (
        <View style={styles.cellBadge}>
          <Text style={styles.cellBadgeText}>
            {LISTING_TYPE_LABEL[d.listingType] ?? d.listingType}
          </Text>
        </View>
      ),
    },
    {
      label: "지역",
      render: (d) => (
        <Text style={styles.cellText}>
          {d.sido ?? "-"} {d.sigungu ?? ""}
        </Text>
      ),
    },
    {
      label: "접수 마감",
      render: (d) => (
        <View>
          <Text style={[styles.cellTextStrong, { color: dDayColor(d.applicationEnd) }]}>
            {dDay(d.applicationEnd)}
          </Text>
          <Text style={styles.cellMuted}>{formatDate(d.applicationEnd)}</Text>
        </View>
      ),
    },
    {
      label: "당첨 발표",
      render: (d) => (
        <View>
          <Text style={[styles.cellTextStrong, { color: dDayColor(d.winnerAnnouncementDate) }]}>
            {dDay(d.winnerAnnouncementDate)}
          </Text>
          <Text style={styles.cellMuted}>{formatDate(d.winnerAnnouncementDate)}</Text>
        </View>
      ),
    },
    {
      label: "입주 예정",
      render: (d) => (
        <View>
          <Text style={[styles.cellTextStrong, { color: dDayColor(d.moveInDate) }]}>
            {dDay(d.moveInDate)}
          </Text>
          <Text style={styles.cellMuted}>{formatDate(d.moveInDate)}</Text>
        </View>
      ),
    },
    { label: "면적", render: (d) => <Text style={styles.cellText}>{sizeRange(d)}</Text> },
    {
      label: "가격",
      render: (d) =>
        RENTAL_TYPES.has(d.listingType) ? (
          <View>
            <Text style={styles.cellText}>보증금 {depositRange(d)}</Text>
            <Text style={styles.cellMuted}>월 {rentRange(d)}</Text>
          </View>
        ) : (
          <Text style={styles.cellText}>{priceRange(d)}</Text>
        ),
    },
    {
      label: "평당가",
      render: (d) =>
        RENTAL_TYPES.has(d.listingType) ? (
          <Text style={styles.cellMuted}>-</Text>
        ) : (
          <Text style={styles.cellText}>{pyungPriceRange(d)}</Text>
        ),
    },
    {
      label: "총 공급",
      render: (d) => <Text style={styles.cellText}>{d.totalSupply ?? "-"}세대</Text>,
    },
  ];

  const excludedItems = favorites.filter((f) => excluded.has(f.id));

  return (
    <View style={styles.container}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false}>
        <View style={styles.tableWrapper}>
          {/* 헤더 */}
          <View style={styles.headerRow}>
            <View style={styles.labelCell}>
              <Text style={styles.labelText}>항목</Text>
            </View>
            {visible.map((d) => (
              <View key={d.id} style={styles.headerCell}>
                <Pressable onPress={() => router.push({ pathname: "/listings/[id]", params: { id: String(d.id) } })}>
                  <Text style={styles.headerName} numberOfLines={2}>
                    {d.name}
                  </Text>
                </Pressable>
                <Pressable
                  onPress={() => setExcluded((s) => new Set(s).add(d.id))}
                  style={styles.removeBtn}
                >
                  <Text style={styles.removeBtnText}>제외</Text>
                </Pressable>
              </View>
            ))}
          </View>

          {/* 데이터 행 */}
          {rows.map((r) => (
            <View key={r.label} style={styles.dataRow}>
              <View style={styles.labelCell}>
                <Text style={styles.labelText}>{r.label}</Text>
              </View>
              {visible.map((d) => (
                <View key={d.id} style={styles.dataCell}>
                  {r.render(d)}
                </View>
              ))}
            </View>
          ))}
        </View>
      </ScrollView>

      {excludedItems.length > 0 && (
        <View style={styles.excludedBox}>
          <Text style={styles.excludedTitle}>비교에서 제외됨</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 6 }}>
            {excludedItems.map((it) => (
              <Pressable
                key={it.id}
                style={styles.excludedChip}
                onPress={() =>
                  setExcluded((s) => {
                    const next = new Set(s);
                    next.delete(it.id);
                    return next;
                  })
                }
              >
                <Text style={styles.excludedChipText}>+ {it.name}</Text>
              </Pressable>
            ))}
          </ScrollView>
        </View>
      )}
    </View>
  );
}

const COL_W = 160;
const LABEL_W = 80;

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#fafafa" },
  center: { flex: 1, alignItems: "center", justifyContent: "center", padding: 32, gap: 8 },
  muted: { fontSize: 13, color: "#71717a", textAlign: "center" },
  title: { fontSize: 16, fontWeight: "600" },
  btn: { backgroundColor: "#2563eb", paddingHorizontal: 18, paddingVertical: 10, borderRadius: 8, marginTop: 8 },
  btnText: { color: "white", fontWeight: "600" },

  tableWrapper: { padding: 12 },
  headerRow: { flexDirection: "row", borderBottomWidth: 1, borderColor: "#e4e4e7", backgroundColor: "white" },
  dataRow: { flexDirection: "row", borderBottomWidth: 1, borderColor: "#f4f4f5", backgroundColor: "white" },
  labelCell: {
    width: LABEL_W,
    padding: 10,
    backgroundColor: "#fafafa",
    borderRightWidth: 1,
    borderColor: "#e4e4e7",
    justifyContent: "center",
  },
  labelText: { fontSize: 11, fontWeight: "600", color: "#52525b" },
  headerCell: { width: COL_W, padding: 10, gap: 6 },
  headerName: { fontSize: 13, fontWeight: "600", color: "#0a0a0a" },
  removeBtn: {
    alignSelf: "flex-start",
    borderWidth: 1,
    borderColor: "#d4d4d8",
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  removeBtnText: { fontSize: 10, color: "#71717a" },
  dataCell: { width: COL_W, padding: 10 },
  cellText: { fontSize: 13, color: "#27272a" },
  cellTextStrong: { fontSize: 14, fontWeight: "600" },
  cellMuted: { fontSize: 11, color: "#a1a1aa" },
  cellBadge: {
    alignSelf: "flex-start",
    backgroundColor: "#dbeafe",
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  cellBadgeText: { fontSize: 11, color: "#1d4ed8", fontWeight: "500" },

  excludedBox: {
    backgroundColor: "white",
    borderTopWidth: 1,
    borderColor: "#e4e4e7",
    padding: 12,
    gap: 8,
  },
  excludedTitle: { fontSize: 11, fontWeight: "600", color: "#52525b" },
  excludedChip: {
    backgroundColor: "#f4f4f5",
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  excludedChipText: { fontSize: 11, color: "#52525b" },
});
