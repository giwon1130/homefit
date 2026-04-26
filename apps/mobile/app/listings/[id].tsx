import { useCallback, useState } from "react";
import {
  ActivityIndicator,
  Linking,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { useFocusEffect, useLocalSearchParams } from "expo-router";
import { apiFetch, LISTING_TYPE_LABEL, type ListingDetail } from "@/lib/api";

function formatDate(iso?: string) {
  if (!iso) return "-";
  try {
    return new Date(iso).toLocaleString("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit" });
  } catch { return iso; }
}

function formatKrw(n?: number) {
  if (n == null) return "-";
  const eok = Math.floor(n / 100_000_000);
  const man = Math.floor((n % 100_000_000) / 10_000);
  if (eok > 0) return `${eok}억${man > 0 ? ` ${man.toLocaleString()}만원` : "원"}`;
  if (man > 0) return `${man.toLocaleString()}만원`;
  return `${n.toLocaleString()}원`;
}

export default function ListingDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const [data, setData] = useState<ListingDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const res = await apiFetch(`/api/v1/listings/${id}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setData(await res.json());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useFocusEffect(useCallback(() => { void load(); }, [load]));

  if (loading) return <View style={styles.center}><ActivityIndicator size="large" /></View>;
  if (error) return <View style={styles.center}><Text style={styles.muted}>{error}</Text></View>;
  if (!data) return null;

  return (
    <ScrollView style={styles.container} contentContainerStyle={{ padding: 16, gap: 16 }}>
      <View style={styles.row}>
        <View style={styles.badge}>
          <Text style={styles.badgeText}>
            {LISTING_TYPE_LABEL[data.listingType] ?? data.listingType}
          </Text>
        </View>
        {data.sido && (
          <Text style={styles.muted}>{data.sido} {data.sigungu ?? ""}</Text>
        )}
      </View>
      <Text style={styles.title}>{data.name}</Text>
      <Text style={styles.muted}>{data.address}</Text>

      <View style={styles.fieldGrid}>
        <Field label="접수 시작" value={formatDate(data.applicationStart)} />
        <Field label="접수 마감" value={formatDate(data.applicationEnd)} />
        <Field label="공고일" value={formatDate(data.announcementDate)} />
        <Field label="입주예정" value={formatDate(data.moveInDate)} />
        <Field label="총 공급" value={data.totalSupply?.toString() ?? "-"} />
      </View>

      {data.units.length > 0 && (
        <View>
          <Text style={styles.sectionTitle}>공급 유형</Text>
          {data.units.map((u) => (
            <View key={u.id} style={styles.unitRow}>
              <Text style={styles.unitType}>{u.unitType ?? "-"}</Text>
              <Text style={styles.unitMeta}>{u.sizeM2 ? `${u.sizeM2}㎡` : "-"}</Text>
              <Text style={styles.unitMeta}>{u.supplyCount ?? "-"}세대</Text>
              <Text style={styles.unitPrice}>{formatKrw(u.priceMaxKrw)}</Text>
            </View>
          ))}
        </View>
      )}

      {data.documentUrl && (
        <Pressable
          style={styles.linkButton}
          onPress={() => Linking.openURL(data.documentUrl!)}
        >
          <Text style={styles.linkButtonText}>원문 공고 보기 →</Text>
        </Pressable>
      )}
    </ScrollView>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <Text style={styles.fieldValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#fafafa" },
  center: { flex: 1, alignItems: "center", justifyContent: "center" },
  row: { flexDirection: "row", alignItems: "center", gap: 8 },
  badge: { backgroundColor: "#dbeafe", paddingHorizontal: 8, paddingVertical: 2, borderRadius: 4 },
  badgeText: { fontSize: 11, color: "#1d4ed8", fontWeight: "500" },
  title: { fontSize: 22, fontWeight: "700" },
  muted: { fontSize: 13, color: "#71717a" },
  fieldGrid: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  field: { width: "48%", backgroundColor: "white", padding: 12, borderRadius: 8, borderWidth: 1, borderColor: "#e4e4e7" },
  fieldLabel: { fontSize: 11, color: "#71717a" },
  fieldValue: { fontSize: 14, fontWeight: "500", marginTop: 2 },
  sectionTitle: { fontSize: 15, fontWeight: "600", marginBottom: 8 },
  unitRow: { flexDirection: "row", justifyContent: "space-between", padding: 10, borderTopWidth: 1, borderColor: "#e4e4e7" },
  unitType: { fontWeight: "500", flex: 1 },
  unitMeta: { color: "#71717a", flex: 1, textAlign: "right" },
  unitPrice: { fontWeight: "600", flex: 1.4, textAlign: "right" },
  linkButton: { backgroundColor: "white", padding: 14, borderRadius: 8, borderWidth: 1, borderColor: "#d4d4d8", alignItems: "center" },
  linkButtonText: { color: "#1d4ed8", fontWeight: "500" },
});
