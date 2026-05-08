import { useCallback, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { Link, useFocusEffect, useRouter } from "expo-router";
import { apiFetch, LISTING_TYPE_LABEL, type ListingSummary } from "@/lib/api";
import { isLoggedIn } from "@/lib/auth";

function dDay(iso?: string): string {
  if (!iso) return "";
  const d = Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000);
  if (d < 0) return "마감";
  if (d === 0) return "오늘";
  return `D-${d}`;
}

export default function FavoritesTab() {
  const router = useRouter();
  const [authed, setAuthed] = useState<boolean | null>(null);
  const [items, setItems] = useState<ListingSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const ok = await isLoggedIn();
      setAuthed(ok);
      if (!ok) return;
      const res = await apiFetch("/api/v1/favorites");
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setItems((await res.json()) as ListingSummary[]);
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

  if (authed === false) {
    return (
      <View style={[styles.container, styles.center]}>
        <Text style={styles.title}>로그인이 필요해요</Text>
        <Text style={styles.muted}>즐겨찾기는 로그인 후에 사용할 수 있어요.</Text>
        <Pressable style={styles.primaryBtn} onPress={() => router.push("/login")}>
          <Text style={styles.primaryBtnText}>로그인</Text>
        </Pressable>
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
      ListHeaderComponent={
        items.length >= 2 ? (
          <Pressable
            style={styles.compareBtn}
            onPress={() => router.push("/favorites/compare")}
          >
            <Text style={styles.compareBtnText}>비교하기 →</Text>
          </Pressable>
        ) : null
      }
      ListEmptyComponent={
        <View style={styles.center}>
          <Text style={styles.muted}>
            {error ?? "아직 즐겨찾기한 청약이 없어요. 상세 화면 ❤ 로 추가하세요."}
          </Text>
        </View>
      }
      renderItem={({ item }) => (
        <Link
          href={{ pathname: "/listings/[id]", params: { id: item.id.toString() } }}
          asChild
        >
          <View style={styles.card}>
            <View style={styles.row}>
              <View style={styles.badge}>
                <Text style={styles.badgeText}>
                  {LISTING_TYPE_LABEL[item.listingType] ?? item.listingType}
                </Text>
              </View>
              <Text style={styles.dDay}>{dDay(item.applicationEnd)}</Text>
            </View>
            <Text style={styles.cardTitle} numberOfLines={2}>
              {item.name}
            </Text>
            {item.sido && (
              <Text style={styles.muted}>
                {item.sido} {item.sigungu ?? ""}
              </Text>
            )}
          </View>
        </Link>
      )}
    />
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#fafafa" },
  list: { padding: 16, gap: 12 },
  center: { flex: 1, alignItems: "center", justifyContent: "center", padding: 32, gap: 12 },
  card: {
    backgroundColor: "white",
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: "#e4e4e7",
  },
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  badge: {
    backgroundColor: "#dbeafe",
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 4,
  },
  badgeText: { fontSize: 11, color: "#1d4ed8", fontWeight: "500" },
  dDay: { fontSize: 12, fontWeight: "700", color: "#0a0a0a" },
  cardTitle: { fontSize: 15, fontWeight: "600", marginTop: 8 },
  title: { fontSize: 18, fontWeight: "700" },
  muted: { fontSize: 12, color: "#71717a", marginTop: 2 },
  primaryBtn: {
    backgroundColor: "#2563eb",
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  primaryBtnText: { color: "white", fontWeight: "600" },
  compareBtn: {
    backgroundColor: "white",
    borderWidth: 1,
    borderColor: "#d4d4d8",
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: "center",
  },
  compareBtnText: { color: "#1d4ed8", fontWeight: "600" },
});
