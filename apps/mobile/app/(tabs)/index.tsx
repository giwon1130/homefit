import { useCallback, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  RefreshControl,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { Link, useFocusEffect } from "expo-router";
import { apiFetch, LISTING_TYPE_LABEL, type ListingPage, type ListingSummary } from "@/lib/api";

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

  const load = useCallback(async () => {
    setError(null);
    try {
      const res = await apiFetch("/api/v1/listings?size=30&sort=CLOSING");
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const page = (await res.json()) as ListingPage;
      setItems(page.content);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
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
          <Text style={styles.muted}>{error ?? "청약이 없어요"}</Text>
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

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#fafafa" },
  list: { padding: 16, gap: 12 },
  center: { flex: 1, alignItems: "center", justifyContent: "center", padding: 32 },
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
