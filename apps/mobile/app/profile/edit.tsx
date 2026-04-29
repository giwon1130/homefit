import { useEffect, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { useRouter } from "expo-router";
import {
  SIDO_OPTIONS,
  type Assets,
  type FullProfile,
  type HouseholdMember,
  type HouseholdRelation,
  type Income,
  type Preferences,
  type ProfileCore,
  type Workplace,
  type WorkplaceOwner,
} from "@/lib/api";
import {
  eokToWon,
  fetchProfile,
  manToWon,
  saveProfile,
  wonToEok,
  wonToMan,
} from "@/lib/profile";

const RELATION_LABEL: Record<HouseholdRelation, string> = {
  SPOUSE: "배우자",
  CHILD: "자녀",
  PARENT: "부모",
  GRANDPARENT: "조부모",
  OTHER: "기타",
};

const OWNER_LABEL: Record<WorkplaceOwner, string> = { SELF: "본인", SPOUSE: "배우자" };

interface CoreState {
  birthDate: string;
  marriageDate: string;
  isHouseholder: "true" | "false" | "";
  isFirstTimeBuyer: "true" | "false" | "";
  noHomeSince: string;
  subscriptionAccountOpenedAt: string;
  subscriptionDepositMonths: string;
  subscriptionDepositTotal: string;
}

interface MemberState {
  relation: HouseholdRelation;
  birthDate: string;
}

interface WorkplaceState {
  owner: WorkplaceOwner;
  label: string;
  address: string;
  arrivalTime: string;
}

interface IncomeState {
  year: number;
  selfMan: string;
  spouseMan: string;
}

interface AssetsState {
  netWorthEok: string;
  realEstateEok: string;
  monthlyDebtMan: string;
}

interface PrefsState {
  maxPurchaseEok: string;
  maxJeonseEok: string;
  maxMonthlyRentMan: string;
  maxDepositEok: string;
  minRooms: string;
  maxCommuteMinutes: string;
  preferredSidos: string[];
}

const emptyCore: CoreState = {
  birthDate: "",
  marriageDate: "",
  isHouseholder: "",
  isFirstTimeBuyer: "",
  noHomeSince: "",
  subscriptionAccountOpenedAt: "",
  subscriptionDepositMonths: "",
  subscriptionDepositTotal: "",
};

function coreFromApi(c: ProfileCore): CoreState {
  return {
    birthDate: c.birthDate ?? "",
    marriageDate: c.marriageDate ?? "",
    isHouseholder: c.isHouseholder == null ? "" : c.isHouseholder ? "true" : "false",
    isFirstTimeBuyer: c.isFirstTimeBuyer == null ? "" : c.isFirstTimeBuyer ? "true" : "false",
    noHomeSince: c.noHomeSince ?? "",
    subscriptionAccountOpenedAt: c.subscriptionAccountOpenedAt ?? "",
    subscriptionDepositMonths: c.subscriptionDepositMonths?.toString() ?? "",
    subscriptionDepositTotal: c.subscriptionDepositTotal?.toString() ?? "",
  };
}

function membersFromApi(list: HouseholdMember[]): MemberState[] {
  return list.map((m) => ({ relation: m.relation, birthDate: m.birthDate ?? "" }));
}

function workplacesFromApi(list: Workplace[]): WorkplaceState[] {
  return list.map((w) => ({
    owner: w.owner,
    label: w.label ?? "",
    address: w.address,
    arrivalTime: (w.arrivalTime ?? "09:00:00").slice(0, 5),
  }));
}

function incomesFromApi(list: Income[]): IncomeState[] {
  if (list.length === 0) {
    return [{ year: new Date().getFullYear() - 1, selfMan: "", spouseMan: "" }];
  }
  return [...list]
    .sort((a, b) => b.year - a.year)
    .map((i) => ({
      year: i.year,
      selfMan: wonToMan(i.selfAmount),
      spouseMan: wonToMan(i.spouseAmount),
    }));
}

function assetsFromApi(a: Assets | null): AssetsState {
  return {
    netWorthEok: wonToEok(a?.netWorth),
    realEstateEok: wonToEok(a?.realEstate),
    monthlyDebtMan: wonToMan(a?.monthlyDebt),
  };
}

function prefsFromApi(p: Preferences | null): PrefsState {
  return {
    maxPurchaseEok: wonToEok(p?.maxPurchasePrice),
    maxJeonseEok: wonToEok(p?.maxJeonsePrice),
    maxMonthlyRentMan: wonToMan(p?.maxMonthlyRent),
    maxDepositEok: wonToEok(p?.maxDepositForRent),
    minRooms: p?.minRooms?.toString() ?? "",
    maxCommuteMinutes: p?.maxCommuteMinutes?.toString() ?? "",
    preferredSidos: p?.preferredSidos ?? [],
  };
}

const dateRe = /^\d{4}-\d{2}-\d{2}$/;
const timeRe = /^\d{2}:\d{2}$/;

export default function ProfileEditScreen() {
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [core, setCore] = useState<CoreState>(emptyCore);
  const [members, setMembers] = useState<MemberState[]>([]);
  const [workplaces, setWorkplaces] = useState<WorkplaceState[]>([]);
  const [incomes, setIncomes] = useState<IncomeState[]>([
    { year: new Date().getFullYear() - 1, selfMan: "", spouseMan: "" },
  ]);
  const [assets, setAssets] = useState<AssetsState>({
    netWorthEok: "",
    realEstateEok: "",
    monthlyDebtMan: "",
  });
  const [prefs, setPrefs] = useState<PrefsState>({
    maxPurchaseEok: "",
    maxJeonseEok: "",
    maxMonthlyRentMan: "",
    maxDepositEok: "",
    minRooms: "",
    maxCommuteMinutes: "",
    preferredSidos: [],
  });

  useEffect(() => {
    void (async () => {
      const data = await fetchProfile();
      if (data) hydrate(data);
      setLoading(false);
    })();
  }, []);

  const hydrate = (data: FullProfile) => {
    setCore(coreFromApi(data.core));
    setMembers(membersFromApi(data.householdMembers));
    setWorkplaces(workplacesFromApi(data.workplaces));
    setIncomes(incomesFromApi(data.incomes));
    setAssets(assetsFromApi(data.assets));
    setPrefs(prefsFromApi(data.preferences));
  };

  const onSave = async () => {
    setError(null);

    // Validation
    const dates: Array<[string, string]> = [
      ["생년월일", core.birthDate],
      ["혼인일", core.marriageDate],
      ["무주택 기산일", core.noHomeSince],
      ["청약통장 가입일", core.subscriptionAccountOpenedAt],
    ];
    for (const [name, v] of dates) {
      if (v && !dateRe.test(v)) {
        setError(`${name}: YYYY-MM-DD 형식으로 입력하세요`);
        return;
      }
    }
    for (const m of members) {
      if (m.birthDate && !dateRe.test(m.birthDate)) {
        setError("가족 생년월일: YYYY-MM-DD 형식");
        return;
      }
    }
    for (const w of workplaces) {
      if (w.address.trim().length === 0) {
        setError("직장 주소를 입력하거나 항목을 삭제하세요");
        return;
      }
      if (!timeRe.test(w.arrivalTime)) {
        setError("출근시각: HH:MM 형식");
        return;
      }
    }

    const incomesPayload: Income[] = [];
    for (const r of incomes) {
      const self = manToWon(r.selfMan);
      const spouse = manToWon(r.spouseMan);
      if (self == null && spouse == null) continue;
      incomesPayload.push({ year: r.year, selfAmount: self, spouseAmount: spouse });
    }

    const assetsPayload: Assets = {
      netWorth: eokToWon(assets.netWorthEok),
      realEstate: eokToWon(assets.realEstateEok),
      monthlyDebt: manToWon(assets.monthlyDebtMan),
    };

    const prefsPayload: Preferences = {
      maxPurchasePrice: eokToWon(prefs.maxPurchaseEok),
      maxJeonsePrice: eokToWon(prefs.maxJeonseEok),
      maxMonthlyRent: manToWon(prefs.maxMonthlyRentMan),
      maxDepositForRent: eokToWon(prefs.maxDepositEok),
      minRooms: prefs.minRooms ? Number(prefs.minRooms) : null,
      maxCommuteMinutes: prefs.maxCommuteMinutes ? Number(prefs.maxCommuteMinutes) : null,
      preferredSidos: prefs.preferredSidos,
    };

    setSaving(true);
    const r = await saveProfile({
      core: {
        birthDate: core.birthDate || null,
        marriageDate: core.marriageDate || null,
        isHouseholder: core.isHouseholder === "" ? null : core.isHouseholder === "true",
        isFirstTimeBuyer: core.isFirstTimeBuyer === "" ? null : core.isFirstTimeBuyer === "true",
        noHomeSince: core.noHomeSince || null,
        subscriptionAccountOpenedAt: core.subscriptionAccountOpenedAt || null,
        subscriptionDepositMonths: core.subscriptionDepositMonths
          ? Number(core.subscriptionDepositMonths)
          : null,
        subscriptionDepositTotal: core.subscriptionDepositTotal
          ? Number(core.subscriptionDepositTotal)
          : null,
      },
      members: members.map((m) => ({
        relation: m.relation,
        birthDate: m.birthDate || null,
      })),
      workplaces: workplaces.map((w) => ({
        owner: w.owner,
        label: w.label || null,
        address: w.address,
        arrivalTime: w.arrivalTime,
      })),
      incomes: incomesPayload,
      assets: assetsPayload,
      preferences: prefsPayload,
    });
    setSaving(false);

    if (r.ok) {
      Alert.alert("저장 완료", "프로필이 업데이트됐어요", [
        { text: "확인", onPress: () => router.back() },
      ]);
    } else {
      setError(r.error);
    }
  };

  if (loading) {
    return (
      <View style={[styles.container, styles.center]}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
    >
      <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
        <Section title="기본 정보">
          <DateField
            label="생년월일"
            value={core.birthDate}
            onChange={(v) => setCore({ ...core, birthDate: v })}
          />
          <DateField
            label="혼인일"
            value={core.marriageDate}
            onChange={(v) => setCore({ ...core, marriageDate: v })}
          />
          <Triple
            label="세대주"
            value={core.isHouseholder}
            onChange={(v) => setCore({ ...core, isHouseholder: v })}
            yesLabel="예"
            noLabel="아니요"
          />
          <Triple
            label="생애최초"
            value={core.isFirstTimeBuyer}
            onChange={(v) => setCore({ ...core, isFirstTimeBuyer: v })}
            yesLabel="해당"
            noLabel="아님"
          />
          <DateField
            label="무주택 기산일"
            value={core.noHomeSince}
            onChange={(v) => setCore({ ...core, noHomeSince: v })}
          />
        </Section>

        <Section title="청약통장">
          <DateField
            label="가입일"
            value={core.subscriptionAccountOpenedAt}
            onChange={(v) => setCore({ ...core, subscriptionAccountOpenedAt: v })}
          />
          <NumField
            label="납입 횟수"
            value={core.subscriptionDepositMonths}
            onChange={(v) => setCore({ ...core, subscriptionDepositMonths: v })}
          />
          <NumField
            label="납입 총액 (원)"
            value={core.subscriptionDepositTotal}
            onChange={(v) => setCore({ ...core, subscriptionDepositTotal: v })}
            placeholder="예: 12000000"
          />
        </Section>

        <Section title="직장 (통근시간 계산용)">
          {workplaces.length === 0 && (
            <Text style={styles.muted}>없음. 추가하면 매칭 통근 점수가 활성화돼요.</Text>
          )}
          {workplaces.map((w, idx) => (
            <View key={idx} style={styles.subCard}>
              <View style={styles.rowGap}>
                <Pill
                  options={[
                    { v: "SELF", label: OWNER_LABEL.SELF },
                    { v: "SPOUSE", label: OWNER_LABEL.SPOUSE },
                  ]}
                  value={w.owner}
                  onChange={(v) =>
                    setWorkplaces(
                      workplaces.map((x, i) =>
                        i === idx ? { ...x, owner: v as WorkplaceOwner } : x,
                      ),
                    )
                  }
                />
                <Pressable
                  onPress={() => setWorkplaces(workplaces.filter((_, i) => i !== idx))}
                  style={styles.removeBtn}
                >
                  <Text style={styles.removeBtnText}>삭제</Text>
                </Pressable>
              </View>
              <LabeledInput
                label="레이블 (선택)"
                value={w.label}
                onChange={(v) =>
                  setWorkplaces(workplaces.map((x, i) => (i === idx ? { ...x, label: v } : x)))
                }
                placeholder="회사명"
              />
              <LabeledInput
                label="주소"
                value={w.address}
                onChange={(v) =>
                  setWorkplaces(workplaces.map((x, i) => (i === idx ? { ...x, address: v } : x)))
                }
                placeholder="예: 서울특별시 강남구 테헤란로 123"
              />
              <LabeledInput
                label="출근시각 (HH:MM)"
                value={w.arrivalTime}
                onChange={(v) =>
                  setWorkplaces(
                    workplaces.map((x, i) => (i === idx ? { ...x, arrivalTime: v } : x)),
                  )
                }
                placeholder="09:00"
              />
            </View>
          ))}
          <AddBtn
            label="+ 직장 추가"
            onPress={() =>
              setWorkplaces([
                ...workplaces,
                { owner: "SELF", label: "", address: "", arrivalTime: "09:00" },
              ])
            }
          />
        </Section>

        <Section title="가족 구성 (본인 제외)">
          {members.length === 0 && <Text style={styles.muted}>없음. 아래에서 추가하세요.</Text>}
          {members.map((m, idx) => (
            <View key={idx} style={styles.subCard}>
              <View style={styles.rowGap}>
                <Pill
                  options={(Object.keys(RELATION_LABEL) as HouseholdRelation[]).map((r) => ({
                    v: r,
                    label: RELATION_LABEL[r],
                  }))}
                  value={m.relation}
                  onChange={(v) =>
                    setMembers(
                      members.map((x, i) =>
                        i === idx ? { ...x, relation: v as HouseholdRelation } : x,
                      ),
                    )
                  }
                />
                <Pressable
                  onPress={() => setMembers(members.filter((_, i) => i !== idx))}
                  style={styles.removeBtn}
                >
                  <Text style={styles.removeBtnText}>삭제</Text>
                </Pressable>
              </View>
              <DateField
                label="생년월일"
                value={m.birthDate}
                onChange={(v) =>
                  setMembers(members.map((x, i) => (i === idx ? { ...x, birthDate: v } : x)))
                }
              />
            </View>
          ))}
          <AddBtn
            label="+ 가족 추가"
            onPress={() => setMembers([...members, { relation: "CHILD", birthDate: "" }])}
          />
        </Section>

        <Section title="연소득 (대출/매칭 자격용, 암호화 저장)">
          {incomes.map((row, idx) => (
            <View key={idx} style={styles.subCard}>
              <View style={styles.rowGap}>
                <View style={{ width: 72 }}>
                  <Text style={styles.label}>연도</Text>
                  <TextInput
                    keyboardType="number-pad"
                    value={row.year.toString()}
                    onChangeText={(v) =>
                      setIncomes(
                        incomes.map((x, i) =>
                          i === idx ? { ...x, year: Number(v) || x.year } : x,
                        ),
                      )
                    }
                    style={styles.input}
                  />
                </View>
                <Pressable
                  onPress={() => setIncomes(incomes.filter((_, i) => i !== idx))}
                  style={styles.removeBtn}
                >
                  <Text style={styles.removeBtnText}>삭제</Text>
                </Pressable>
              </View>
              <LabeledInput
                label="본인 (만원)"
                value={row.selfMan}
                onChange={(v) =>
                  setIncomes(incomes.map((x, i) => (i === idx ? { ...x, selfMan: v } : x)))
                }
                keyboardType="number-pad"
                placeholder="5000"
              />
              <LabeledInput
                label="배우자 (만원)"
                value={row.spouseMan}
                onChange={(v) =>
                  setIncomes(incomes.map((x, i) => (i === idx ? { ...x, spouseMan: v } : x)))
                }
                keyboardType="number-pad"
                placeholder="4000"
              />
            </View>
          ))}
          <AddBtn
            label="+ 이전 연도 추가"
            onPress={() => {
              const minYear =
                incomes.length > 0
                  ? Math.min(...incomes.map((r) => r.year)) - 1
                  : new Date().getFullYear() - 1;
              setIncomes([...incomes, { year: minYear, selfMan: "", spouseMan: "" }]);
            }}
          />
        </Section>

        <Section title="자산 / 채무 (DSR 정밀화용)">
          <LabeledInput
            label="순자산 (억원)"
            value={assets.netWorthEok}
            onChange={(v) => setAssets({ ...assets, netWorthEok: v })}
            keyboardType="decimal-pad"
            placeholder="3"
          />
          <LabeledInput
            label="부동산 평가액 (억원)"
            value={assets.realEstateEok}
            onChange={(v) => setAssets({ ...assets, realEstateEok: v })}
            keyboardType="decimal-pad"
            placeholder="2"
          />
          <LabeledInput
            label="기존 월 채무 상환 (만원/월)"
            value={assets.monthlyDebtMan}
            onChange={(v) => setAssets({ ...assets, monthlyDebtMan: v })}
            keyboardType="number-pad"
            placeholder="50"
          />
        </Section>

        <Section title="예산 및 선호">
          <LabeledInput
            label="매매 예산 (억원)"
            value={prefs.maxPurchaseEok}
            onChange={(v) => setPrefs({ ...prefs, maxPurchaseEok: v })}
            keyboardType="decimal-pad"
            placeholder="8"
          />
          <LabeledInput
            label="전세 예산 (억원)"
            value={prefs.maxJeonseEok}
            onChange={(v) => setPrefs({ ...prefs, maxJeonseEok: v })}
            keyboardType="decimal-pad"
            placeholder="5"
          />
          <LabeledInput
            label="월세 한도 (만원/월)"
            value={prefs.maxMonthlyRentMan}
            onChange={(v) => setPrefs({ ...prefs, maxMonthlyRentMan: v })}
            keyboardType="number-pad"
            placeholder="80"
          />
          <LabeledInput
            label="보증금 한도 (억원)"
            value={prefs.maxDepositEok}
            onChange={(v) => setPrefs({ ...prefs, maxDepositEok: v })}
            keyboardType="decimal-pad"
            placeholder="1"
          />
          <LabeledInput
            label="최소 방 수"
            value={prefs.minRooms}
            onChange={(v) => setPrefs({ ...prefs, minRooms: v })}
            keyboardType="number-pad"
            placeholder="3"
          />
          <LabeledInput
            label="최대 통근 시간 (분)"
            value={prefs.maxCommuteMinutes}
            onChange={(v) => setPrefs({ ...prefs, maxCommuteMinutes: v })}
            keyboardType="number-pad"
            placeholder="60"
          />
          <View style={{ marginTop: 8 }}>
            <Text style={styles.label}>선호 지역 (복수 선택)</Text>
            <View style={styles.chipWrap}>
              {SIDO_OPTIONS.map(({ short, full }) => {
                const on = prefs.preferredSidos.includes(full);
                return (
                  <Pressable
                    key={full}
                    onPress={() =>
                      setPrefs({
                        ...prefs,
                        preferredSidos: on
                          ? prefs.preferredSidos.filter((s) => s !== full)
                          : [...prefs.preferredSidos, full],
                      })
                    }
                    style={[styles.chip, on ? styles.chipActive : styles.chipIdle]}
                  >
                    <Text style={on ? styles.chipTextActive : styles.chipTextIdle}>{short}</Text>
                  </Pressable>
                );
              })}
            </View>
          </View>
        </Section>

        {error && (
          <View style={styles.errorBox}>
            <Text style={styles.errorText}>{error}</Text>
          </View>
        )}

        <Pressable
          style={[styles.saveBtn, saving && { opacity: 0.5 }]}
          disabled={saving}
          onPress={onSave}
        >
          <Text style={styles.saveBtnText}>{saving ? "저장 중…" : "저장"}</Text>
        </Pressable>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

// ----- small components -----

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>{title}</Text>
      <View style={{ gap: 12 }}>{children}</View>
    </View>
  );
}

function LabeledInput({
  label,
  value,
  onChange,
  placeholder,
  keyboardType,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  keyboardType?: "default" | "number-pad" | "decimal-pad";
}) {
  return (
    <View>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        value={value}
        onChangeText={onChange}
        placeholder={placeholder}
        keyboardType={keyboardType ?? "default"}
        autoCapitalize="none"
        autoCorrect={false}
        style={styles.input}
      />
    </View>
  );
}

function DateField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <LabeledInput
      label={label}
      value={value}
      onChange={onChange}
      placeholder="YYYY-MM-DD"
    />
  );
}

function NumField({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
}) {
  return (
    <LabeledInput
      label={label}
      value={value}
      onChange={onChange}
      placeholder={placeholder}
      keyboardType="number-pad"
    />
  );
}

function Triple({
  label,
  value,
  onChange,
  yesLabel,
  noLabel,
}: {
  label: string;
  value: "true" | "false" | "";
  onChange: (v: "true" | "false" | "") => void;
  yesLabel: string;
  noLabel: string;
}) {
  const opts: Array<{ v: "true" | "false" | ""; label: string }> = [
    { v: "true", label: yesLabel },
    { v: "false", label: noLabel },
    { v: "", label: "미지정" },
  ];
  return (
    <View>
      <Text style={styles.label}>{label}</Text>
      <View style={styles.tripleRow}>
        {opts.map((o) => {
          const on = o.v === value;
          return (
            <Pressable
              key={o.v || "none"}
              onPress={() => onChange(o.v)}
              style={[styles.tripleBtn, on ? styles.tripleBtnActive : styles.tripleBtnIdle]}
            >
              <Text style={on ? styles.tripleTextActive : styles.tripleTextIdle}>{o.label}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

function Pill<T extends string>({
  options,
  value,
  onChange,
}: {
  options: Array<{ v: T; label: string }>;
  value: T;
  onChange: (v: T) => void;
}) {
  return (
    <View style={[styles.tripleRow, { flex: 1 }]}>
      {options.map((o) => {
        const on = o.v === value;
        return (
          <Pressable
            key={o.v}
            onPress={() => onChange(o.v)}
            style={[styles.tripleBtn, on ? styles.tripleBtnActive : styles.tripleBtnIdle]}
          >
            <Text style={on ? styles.tripleTextActive : styles.tripleTextIdle}>{o.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

function AddBtn({ label, onPress }: { label: string; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={styles.addBtn}>
      <Text style={styles.addBtnText}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#fafafa" },
  center: { alignItems: "center", justifyContent: "center" },
  scroll: { padding: 16, gap: 16, paddingBottom: 48 },
  section: {
    backgroundColor: "white",
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: "#e4e4e7",
    gap: 12,
  },
  sectionTitle: { fontSize: 15, fontWeight: "700", color: "#0a0a0a" },
  label: { fontSize: 12, color: "#71717a", marginBottom: 4 },
  muted: { fontSize: 13, color: "#71717a" },
  input: {
    borderWidth: 1,
    borderColor: "#e4e4e7",
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
    backgroundColor: "white",
  },
  subCard: {
    backgroundColor: "#fafafa",
    borderRadius: 8,
    padding: 12,
    borderWidth: 1,
    borderColor: "#e4e4e7",
    gap: 10,
  },
  rowGap: { flexDirection: "row", alignItems: "center", gap: 8 },
  removeBtn: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: "#d4d4d8",
    backgroundColor: "white",
  },
  removeBtnText: { fontSize: 12, color: "#52525b" },
  addBtn: {
    borderWidth: 1,
    borderColor: "#a1a1aa",
    borderStyle: "dashed",
    borderRadius: 8,
    paddingVertical: 10,
    alignItems: "center",
  },
  addBtnText: { color: "#52525b", fontSize: 13 },
  tripleRow: { flexDirection: "row", gap: 6 },
  tripleBtn: {
    flex: 1,
    paddingVertical: 8,
    paddingHorizontal: 10,
    borderRadius: 6,
    borderWidth: 1,
    alignItems: "center",
  },
  tripleBtnIdle: { borderColor: "#d4d4d8", backgroundColor: "white" },
  tripleBtnActive: { borderColor: "#2563eb", backgroundColor: "#dbeafe" },
  tripleTextIdle: { color: "#52525b", fontSize: 12, fontWeight: "500" },
  tripleTextActive: { color: "#1d4ed8", fontSize: 12, fontWeight: "600" },
  chipWrap: { flexDirection: "row", flexWrap: "wrap", gap: 6, marginTop: 6 },
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 999,
    borderWidth: 1,
  },
  chipIdle: { borderColor: "#d4d4d8", backgroundColor: "white" },
  chipActive: { borderColor: "#2563eb", backgroundColor: "#2563eb" },
  chipTextIdle: { color: "#3f3f46", fontSize: 12, fontWeight: "500" },
  chipTextActive: { color: "white", fontSize: 12, fontWeight: "600" },
  saveBtn: {
    backgroundColor: "#2563eb",
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: "center",
    marginTop: 8,
  },
  saveBtnText: { color: "white", fontSize: 15, fontWeight: "700" },
  errorBox: {
    backgroundColor: "#fee2e2",
    borderRadius: 8,
    padding: 12,
    borderWidth: 1,
    borderColor: "#fecaca",
  },
  errorText: { color: "#991b1b", fontSize: 13 },
});
