import { useCallback, useMemo, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { Link, useFocusEffect } from "expo-router";
import {
  apiFetch,
  LISTING_TYPE_LABEL,
  SIDO_OPTIONS,
  TYPE_OPTIONS,
  type ListingPage,
  type ListingSummary,
  type ListingType,
} from "@/lib/api";

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

export default function ListingsTab() {
  const [items, setItems] = useState<ListingSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [sido, setSido] = useState<string | null>(null);
  const [type, setType] = useState<ListingType | null>(null);

  const queryString = useMemo(() => {
    const p = new URLSearchParams();
    p.set("size", "30");
    p.set("sort", "CLOSING");
    if (sido) p.set("sido", sido);
    if (type) p.set("type", type);
    return p.toString();
  }, [sido, type]);

  const load = useCallback(async () => {
    setError(null);
    try {
      const res = await apiFetch(`/api/v1/listings?${queryString}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const page = (await res.json()) as ListingPage;
      setItems(page.content);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [queryString]);

  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  const Header = (
    <View style={styles.filters}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.chipsRow}
      >
        <Chip label="전체" active={sido === null} onPress={() => setSido(null)} />
        {SIDO_OPTIONS.map((s) => (
          <Chip
            key={s.full}
            label={s.short}
            active={sido === s.full}
            onPress={() => setSido(sido === s.full ? null : s.full)}
          />
        ))}
      </ScrollView>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.chipsRow}
      >
        <Chip label="모든 유형" active={type === null} onPress={() => setType(null)} />
        {TYPE_OPTIONS.map((t) => (
          <Chip
            key={t}
            label={LISTING_TYPE_LABEL[t]}
            active={type === t}
            onPress={() => setType(type === t ? null : t)}
          />
        ))}
      </ScrollView>
    </View>
  );

  if (loading) {
    return (
      <View style={[styles.container, styles.center]}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  return (
    <FlatList
      style={styles.container}
      contentContainerStyle={styles.list}
      data={items}
      keyExtractor={(it) => it.id.toString()}
      ListHeaderComponent={Header}
      refreshControl={
        <RefreshControl
          refreshing={refreshing}
          onRefresh={() => {
            setRefreshing(true);
            void load();
          }}
        />
      }
      ListEmptyComponent={
        <View style={styles.center}>
          <Text style={styles.muted}>{error ?? "조건에 맞는 청약이 없어요"}</Text>
        </View>
      }
      renderItem={({ item }) => (
        <Link
          href={{ pathname: "/listings/[id]", params: { id: item.id.toString() } }}
          asChild
        >
          <View style={styles.card}>
            <View style={styles.row}>
              <View style={styles.badgeBlue}>
                <Text style={styles.badgeText}>
                  {LISTING_TYPE_LABEL[item.listingType] ?? item.listingType}
                </Text>
              </View>
              <Text style={styles.dDay}>{daysUntil(item.applicationEnd)}</Text>
            </View>
            <Text style={styles.title} numberOfLines={2}>
              {item.name}
            </Text>
            {item.sido && (
              <Text style={styles.muted}>
                {item.sido} {item.sigungu ?? ""}
              </Text>
            )}
            <Text style={styles.smallMuted}>
              접수 {formatDate(item.applicationStart)} ~ {formatDate(item.applicationEnd)}
            </Text>
          </View>
        </Link>
      )}
    />
  );
}

function Chip({
  label,
  active,
  onPress,
}: {
  label: string;
  active: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      style={[styles.chip, active ? styles.chipActive : styles.chipIdle]}
    >
      <Text style={active ? styles.chipTextActive : styles.chipTextIdle}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#fafafa" },
  list: { padding: 16, gap: 12 },
  center: { flex: 1, alignItems: "center", justifyContent: "center", padding: 32 },
  filters: { gap: 8, marginBottom: 4 },
  chipsRow: { gap: 6, paddingRight: 16 },
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 999,
    borderWidth: 1,
  },
  chipIdle: { backgroundColor: "white", borderColor: "#d4d4d8" },
  chipActive: { backgroundColor: "#2563eb", borderColor: "#2563eb" },
  chipTextIdle: { color: "#3f3f46", fontSize: 12, fontWeight: "500" },
  chipTextActive: { color: "white", fontSize: 12, fontWeight: "600" },
  card: {
    backgroundColor: "white",
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: "#e4e4e7",
  },
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  badgeBlue: {
    backgroundColor: "#dbeafe",
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 4,
  },
  badgeText: { fontSize: 11, color: "#1d4ed8", fontWeight: "500" },
  dDay: { fontSize: 12, fontWeight: "700", color: "#0a0a0a" },
  title: { fontSize: 15, fontWeight: "600", marginTop: 8 },
  muted: { fontSize: 12, color: "#71717a", marginTop: 2 },
  smallMuted: { fontSize: 11, color: "#a1a1aa", marginTop: 8 },
});
