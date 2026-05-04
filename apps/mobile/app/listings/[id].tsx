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
import {
  apiFetch,
  LISTING_TYPE_LABEL,
  SUPPLY_TYPE_LABEL,
  type EligibilityResp,
  type ListingDetail,
  type LoanEstimateResp,
} from "@/lib/api";
import { isLoggedIn as isAuthed } from "@/lib/auth";

function formatDate(iso?: string) {
  if (!iso) return "-";
  try {
    return new Date(iso).toLocaleString("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit" });
  } catch { return iso; }
}

function formatKrw(n?: number | null) {
  if (n == null) return "-";
  const eok = Math.floor(n / 100_000_000);
  const man = Math.floor((n % 100_000_000) / 10_000);
  if (eok > 0) return `${eok}억${man > 0 ? ` ${man.toLocaleString()}만원` : "원"}`;
  if (man > 0) return `${man.toLocaleString()}만원`;
  return `${n.toLocaleString()}원`;
}

function dayDelta(iso?: string): { label: string; tone: "urgent" | "soon" | "normal" | "past" } | null {
  if (!iso) return null;
  const d = Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000);
  if (d < 0) return { label: "지남", tone: "past" };
  if (d === 0) return { label: "오늘", tone: "urgent" };
  return { label: `D-${d}`, tone: d <= 3 ? "urgent" : d <= 14 ? "soon" : "normal" };
}

function pyungPriceMan(price?: number | null, sizeM2?: number | null): number | null {
  if (price == null || sizeM2 == null || sizeM2 <= 0) return null;
  const pyung = sizeM2 * 0.3025;
  return Math.round(price / pyung / 10_000);
}

const RENTAL_TYPES = new Set([
  "HAPPY_HOUSE",
  "PURCHASE_RENTAL",
  "JEONSE_RENTAL",
  "NATIONAL_RENTAL",
]);

export default function ListingDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const [data, setData] = useState<ListingDetail | null>(null);
  const [eligibility, setEligibility] = useState<EligibilityResp | null>(null);
  const [loan, setLoan] = useState<LoanEstimateResp | null>(null);
  const [favorited, setFavorited] = useState(false);
  const [authed, setAuthed] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const auth = await isAuthed();
      setAuthed(auth);

      const detailRes = await apiFetch(`/api/v1/listings/${id}`);
      if (!detailRes.ok) throw new Error(`HTTP ${detailRes.status}`);
      setData(await detailRes.json());

      if (auth) {
        const [er, lr, fr] = await Promise.all([
          apiFetch(`/api/v1/listings/${id}/eligibility`),
          apiFetch(`/api/v1/listings/${id}/loan-estimate`),
          apiFetch("/api/v1/favorites"),
        ]);
        if (er.ok) setEligibility(await er.json());
        if (lr.ok) setLoan(await lr.json());
        if (fr.ok) {
          const list = (await fr.json()) as Array<{ id: number }>;
          setFavorited(list.some((x) => x.id === Number(id)));
        }
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useFocusEffect(useCallback(() => { void load(); }, [load]));

  const toggleFavorite = useCallback(async () => {
    const res = await apiFetch(`/api/v1/favorites/${id}`, { method: favorited ? "DELETE" : "PUT" });
    if (res.ok) setFavorited(!favorited);
  }, [id, favorited]);

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

      <View style={styles.titleRow}>
        <Text style={styles.title}>{data.name}</Text>
        {authed && (
          <Pressable
            onPress={toggleFavorite}
            style={[styles.favoriteButton, favorited && styles.favoriteButtonActive]}
          >
            <Text style={favorited ? styles.favoriteTextActive : styles.favoriteText}>
              {favorited ? "♥ 저장됨" : "♡ 저장"}
            </Text>
          </Pressable>
        )}
      </View>
      <Text style={styles.muted}>{data.address}</Text>

      <DDayStrip listing={data} />

      {eligibility && (
        <View style={styles.eligibilityCard}>
          <Text style={styles.cardTitle}>내 자격 판정</Text>
          {eligibility.bestSupplyType ? (
            <Text style={styles.cardSubtitle}>
              추천: {SUPPLY_TYPE_LABEL[eligibility.bestSupplyType]}
            </Text>
          ) : (
            <Text style={styles.muted}>가능한 유형이 없습니다.</Text>
          )}
          {eligibility.details.map((d) => (
            <View key={d.supplyType} style={[styles.subCard, !d.eligible && { opacity: 0.55 }]}>
              <View style={styles.row}>
                <Text style={{ fontWeight: "500", flex: 1 }}>
                  {SUPPLY_TYPE_LABEL[d.supplyType]}
                </Text>
                <Text style={d.eligible ? styles.okText : styles.muted}>
                  {d.eligible ? "가능" : "불가"}
                </Text>
              </View>
              {d.reasons.map((r, i) => (
                <Text key={i} style={styles.reasonText}>• {r}</Text>
              ))}
            </View>
          ))}
        </View>
      )}

      {loan && (
        <View style={styles.loanCard}>
          <Text style={styles.cardTitle}>내 대출 가능액 (추정)</Text>
          {loan.recommended && loan.recommended.limitKrw ? (
            <View style={styles.subCard}>
              <Text style={styles.muted}>추천 상품</Text>
              <Text style={{ fontSize: 16, fontWeight: "600", marginTop: 4 }}>
                {loan.recommended.name} · {formatKrw(loan.recommended.limitKrw)}
              </Text>
              {loan.selfFundingKrw != null && (
                <Text style={styles.muted}>
                  자기부담 약 {formatKrw(loan.selfFundingKrw)}
                </Text>
              )}
            </View>
          ) : (
            <Text style={styles.muted}>적용 가능 상품 없음. 프로필 소득을 확인해주세요.</Text>
          )}
          {loan.products.map((p) => (
            <View key={p.name} style={[styles.subCard, !p.eligible && { opacity: 0.55 }]}>
              <View style={styles.row}>
                <Text style={{ fontWeight: "500", flex: 1 }}>{p.name}</Text>
                <Text style={p.eligible ? styles.okText : styles.muted}>
                  {p.eligible
                    ? p.limitKrw ? `${formatKrw(p.limitKrw)}` : "가능"
                    : "불가"}
                </Text>
              </View>
              {p.reasons.map((r, i) => (
                <Text key={i} style={styles.reasonText}>• {r}</Text>
              ))}
            </View>
          ))}
          <Text style={[styles.muted, { marginTop: 8, fontSize: 11 }]}>
            ⚠ 단순 추정. 실제 한도는 기관 시뮬레이터로 확인.
          </Text>
        </View>
      )}

      <View style={styles.fieldGrid}>
        <Field label="접수 시작" value={formatDate(data.applicationStart)} />
        <Field label="접수 마감" value={formatDate(data.applicationEnd)} />
        <Field label="공고일" value={formatDate(data.announcementDate)} />
        <Field label="당첨 발표" value={formatDate(data.winnerAnnouncementDate)} />
        <Field label="입주예정" value={formatDate(data.moveInDate)} />
        <Field label="총 공급" value={data.totalSupply?.toString() ?? "-"} />
      </View>

      {data.units.length > 0 && (
        <View>
          <Text style={styles.sectionTitle}>공급 유형</Text>
          {data.units.map((u) => {
            const isRental = RENTAL_TYPES.has(data.listingType);
            const pp = pyungPriceMan(u.priceMaxKrw, u.sizeM2);
            return (
              <View key={u.id} style={styles.unitRow}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.unitType}>{u.unitType ?? "-"}</Text>
                  <Text style={styles.unitMeta}>
                    {u.sizeM2 ? `${u.sizeM2}㎡` : "-"} · {u.supplyCount ?? "-"}세대
                  </Text>
                </View>
                <View style={{ alignItems: "flex-end" }}>
                  {!isRental && u.priceMaxKrw ? (
                    <>
                      <Text style={styles.unitPrice}>{formatKrw(u.priceMaxKrw)}</Text>
                      {pp != null && (
                        <Text style={styles.unitMeta}>평당 {pp.toLocaleString()}만원</Text>
                      )}
                    </>
                  ) : null}
                  {isRental && u.depositAmount ? (
                    <Text style={styles.unitMeta}>보증금 {formatKrw(u.depositAmount)}</Text>
                  ) : null}
                  {isRental && u.monthlyRent ? (
                    <Text style={styles.unitMeta}>월 {formatKrw(u.monthlyRent)}</Text>
                  ) : null}
                </View>
              </View>
            );
          })}
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

function DDayStrip({ listing }: { listing: ListingDetail }) {
  const items = [
    { label: "접수 마감", iso: listing.applicationEnd },
    { label: "당첨 발표", iso: listing.winnerAnnouncementDate },
    { label: "입주예정", iso: listing.moveInDate },
  ];
  const deltas = items.map((i) => ({ ...i, delta: dayDelta(i.iso) }));
  if (!deltas.some((d) => d.delta != null)) return null;
  return (
    <View style={styles.ddayStrip}>
      {deltas.map((d) => {
        const tone = d.delta?.tone ?? "past";
        const wrapStyle = [
          styles.ddayCard,
          tone === "urgent" && styles.ddayUrgent,
          tone === "soon" && styles.ddaySoon,
          tone === "past" && styles.ddayPast,
        ];
        const labelStyle = [
          styles.ddayLabel,
          tone === "urgent" && { color: "#b91c1c" },
          tone === "soon" && { color: "#b45309" },
          tone === "past" && { color: "#a1a1aa" },
        ];
        return (
          <View key={d.label} style={wrapStyle}>
            <Text style={styles.ddayHead}>{d.label}</Text>
            <Text style={labelStyle}>{d.delta?.label ?? "-"}</Text>
            <Text style={styles.ddayDate}>
              {d.iso ? new Date(d.iso).toLocaleDateString("ko-KR", { month: "2-digit", day: "2-digit" }) : "-"}
            </Text>
          </View>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#fafafa" },
  center: { flex: 1, alignItems: "center", justifyContent: "center" },
  row: { flexDirection: "row", alignItems: "center", gap: 8 },
  titleRow: { flexDirection: "row", alignItems: "flex-start", justifyContent: "space-between", gap: 12 },
  badge: { backgroundColor: "#dbeafe", paddingHorizontal: 8, paddingVertical: 2, borderRadius: 4 },
  badgeText: { fontSize: 11, color: "#1d4ed8", fontWeight: "500" },
  title: { fontSize: 22, fontWeight: "700", flex: 1 },
  muted: { fontSize: 13, color: "#71717a" },
  okText: { fontSize: 13, color: "#15803d", fontWeight: "500" },
  reasonText: { fontSize: 12, color: "#525252", marginTop: 2 },

  favoriteButton: { borderWidth: 1, borderColor: "#d4d4d8", borderRadius: 6, paddingHorizontal: 10, paddingVertical: 6 },
  favoriteButtonActive: { backgroundColor: "#fef2f2", borderColor: "#fca5a5" },
  favoriteText: { color: "#525252", fontSize: 13 },
  favoriteTextActive: { color: "#b91c1c", fontSize: 13, fontWeight: "500" },

  eligibilityCard: { backgroundColor: "#eff6ff", padding: 12, borderRadius: 8, borderWidth: 1, borderColor: "#bfdbfe", gap: 8 },
  loanCard: { backgroundColor: "#fef3c7", padding: 12, borderRadius: 8, borderWidth: 1, borderColor: "#fde68a", gap: 8 },
  cardTitle: { fontSize: 15, fontWeight: "600" },
  cardSubtitle: { fontSize: 13, color: "#1e40af" },
  subCard: { backgroundColor: "white", padding: 10, borderRadius: 6 },

  ddayStrip: { flexDirection: "row", gap: 8 },
  ddayCard: {
    flex: 1,
    backgroundColor: "white",
    borderRadius: 8,
    borderWidth: 1,
    borderColor: "#e4e4e7",
    padding: 10,
    alignItems: "flex-start",
  },
  ddayUrgent: { backgroundColor: "#fef2f2", borderColor: "#fecaca" },
  ddaySoon: { backgroundColor: "#fffbeb", borderColor: "#fde68a" },
  ddayPast: { opacity: 0.5 },
  ddayHead: { fontSize: 11, color: "#71717a" },
  ddayLabel: { fontSize: 22, fontWeight: "700", marginTop: 2 },
  ddayDate: { fontSize: 11, color: "#a1a1aa", marginTop: 2 },

  fieldGrid: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  field: { width: "48%", backgroundColor: "white", padding: 12, borderRadius: 8, borderWidth: 1, borderColor: "#e4e4e7" },
  fieldLabel: { fontSize: 11, color: "#71717a" },
  fieldValue: { fontSize: 14, fontWeight: "500", marginTop: 2 },
  sectionTitle: { fontSize: 15, fontWeight: "600", marginBottom: 8 },
  unitRow: { flexDirection: "row", padding: 10, borderTopWidth: 1, borderColor: "#e4e4e7" },
  unitType: { fontWeight: "500" },
  unitMeta: { color: "#71717a", fontSize: 12 },
  unitPrice: { fontWeight: "600" },
  linkButton: { backgroundColor: "white", padding: 14, borderRadius: 8, borderWidth: 1, borderColor: "#d4d4d8", alignItems: "center" },
  linkButtonText: { color: "#1d4ed8", fontWeight: "500" },
});
