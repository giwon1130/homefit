import { useCallback, useState } from "react";
import { ActivityIndicator, FlatList, StyleSheet, Text, View, Pressable } from "react-native";
import { Link, useFocusEffect, useRouter } from "expo-router";
import {
  apiFetch,
  LISTING_TYPE_LABEL,
  type MatchedListing,
  type MatchedListingPage,
} from "@/lib/api";
import { isLoggedIn } from "@/lib/auth";

export default function MatchTab() {
  const router = useRouter();
  const [authed, setAuthed] = useState<boolean | null>(null);
  const [items, setItems] = useState<MatchedListing[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const ok = await isLoggedIn();
      setAuthed(ok);
      if (!ok) return;
      const res = await apiFetch("/api/v1/listings/match?size=20");
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = (await res.json()) as MatchedListingPage;
      setItems(data.content);
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
        <Text style={styles.muted}>내 조건 맞춤 매칭은 로그인 후에 볼 수 있어요.</Text>
        <Pressable style={styles.button} onPress={() => router.push("/login")}>
          <Text style={styles.buttonText}>로그인</Text>
        </Pressable>
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

  return (
    <FlatList
      style={styles.container}
      contentContainerStyle={styles.list}
      data={items}
      keyExtractor={(r) => r.listing.id.toString()}
      ListHeaderComponent={
        <Text style={styles.headerHint}>
          내 프로필(예산/직장/선호) 기반 점수 — 프로필을 더 채울수록 정확해져요.
        </Text>
      }
      ListEmptyComponent={
        <View style={styles.center}>
          <Text style={styles.muted}>매칭되는 청약이 없습니다. 프로필을 채워보세요.</Text>
          <Pressable style={styles.button} onPress={() => router.push("/profile/edit")}>
            <Text style={styles.buttonText}>프로필 입력</Text>
          </Pressable>
        </View>
      }
      renderItem={({ item: { listing: l, score } }) => (
        <Link
          href={{ pathname: "/listings/[id]", params: { id: l.id.toString() } }}
          asChild
        >
          <View style={styles.card}>
            <View style={styles.cardRow}>
              <ScoreCircle total={score.total} max={score.max} />
              <View style={{ flex: 1, marginLeft: 12 }}>
                <View style={styles.badgeRow}>
                  <View style={styles.badgeBlue}>
                    <Text style={styles.badgeText}>
                      {LISTING_TYPE_LABEL[l.listingType] ?? l.listingType}
                    </Text>
                  </View>
                  {l.sido && <Text style={styles.muted}>{l.sido} {l.sigungu ?? ""}</Text>}
                </View>
                <Text style={styles.title} numberOfLines={2}>{l.name}</Text>
              </View>
            </View>
            <View style={styles.barGrid}>
              <Bar label="자격" v={score.eligibility} max={25} />
              <Bar label="예산" v={score.budget} max={25} />
              <Bar label="지역" v={score.region} max={20} />
              <Bar
                label={score.commuteMinutes != null ? `통근 ${score.commuteMinutes}분` : "통근"}
                v={score.commute}
                max={30}
              />
            </View>
            {score.notes && score.notes.length > 0 && (
              <View style={styles.notes}>
                {score.notes.slice(0, 3).map((n, i) => (
                  <Text key={i} style={styles.noteText}>• {n}</Text>
                ))}
              </View>
            )}
          </View>
        </Link>
      )}
    />
  );
}

function ScoreCircle({ total, max }: { total: number; max: number }) {
  const ratio = max ? total / max : 0;
  const bg = ratio >= 0.7 ? "#10b981" : ratio >= 0.4 ? "#3b82f6" : "#a1a1aa";
  return (
    <View style={[styles.circle, { backgroundColor: bg }]}>
      <Text style={styles.circleNum}>{total}</Text>
      <Text style={styles.circleMax}>/ {max}</Text>
    </View>
  );
}

function Bar({ label, v, max }: { label: string; v: number; max: number }) {
  const pct = max ? Math.max(0, Math.min(100, Math.round((v / max) * 100))) : 0;
  const color = pct >= 70 ? "#10b981" : pct >= 40 ? "#3b82f6" : "#a1a1aa";
  return (
    <View style={styles.barCol}>
      <View style={styles.barLabelRow}>
        <Text style={styles.barLabel}>{label}</Text>
        <Text style={styles.barValue}>{v}/{max}</Text>
      </View>
      <View style={styles.barBg}>
        <View style={[styles.barFill, { width: `${pct}%`, backgroundColor: color }]} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#fafafa" },
  list: { padding: 16, gap: 12 },
  center: { flex: 1, alignItems: "center", justifyContent: "center", padding: 32, gap: 8 },
  headerHint: { fontSize: 12, color: "#71717a", marginBottom: 4 },
  card: { backgroundColor: "white", borderRadius: 12, padding: 16, borderWidth: 1, borderColor: "#e4e4e7" },
  cardRow: { flexDirection: "row", alignItems: "flex-start" },
  badgeRow: { flexDirection: "row", alignItems: "center", gap: 8 },
  badgeBlue: { backgroundColor: "#dbeafe", paddingHorizontal: 8, paddingVertical: 2, borderRadius: 4 },
  badgeText: { fontSize: 11, color: "#1d4ed8", fontWeight: "500" },
  title: { fontSize: 15, fontWeight: "600", marginTop: 6 },
  muted: { fontSize: 12, color: "#71717a" },
  barGrid: {
    marginTop: 12,
    flexDirection: "row",
    flexWrap: "wrap",
    rowGap: 8,
  },
  barCol: { width: "50%", paddingRight: 8 },
  barLabelRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  barLabel: { fontSize: 11, color: "#52525b", fontWeight: "500" },
  barValue: { fontSize: 10, color: "#a1a1aa" },
  barBg: { height: 6, backgroundColor: "#f4f4f5", borderRadius: 3, marginTop: 3, overflow: "hidden" },
  barFill: { height: "100%" },
  notes: { marginTop: 10, gap: 2, paddingTop: 8, borderTopWidth: 1, borderTopColor: "#f4f4f5" },
  noteText: { fontSize: 11, color: "#71717a", lineHeight: 16 },
  circle: { width: 56, height: 56, borderRadius: 28, alignItems: "center", justifyContent: "center" },
  circleNum: { color: "white", fontWeight: "700", fontSize: 18, lineHeight: 20 },
  circleMax: { color: "white", fontSize: 10, opacity: 0.8 },
  button: { backgroundColor: "#2563eb", paddingHorizontal: 24, paddingVertical: 12, borderRadius: 8, marginTop: 12 },
  buttonText: { color: "white", fontWeight: "600" },
});
