"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";
import { apiFetch, type HouseholdRelation, type WorkplaceOwner } from "@/lib/api";

const dateOrNull = z.preprocess(
  (v) => (v === "" || v == null ? null : v),
  z.string().date().nullable(),
);
const intOrNull = z.preprocess(
  (v) => (v === "" || v == null ? null : Number(v)),
  z.number().int().nullable(),
);
const longOrNull = z.preprocess(
  (v) => (v === "" || v == null ? null : Number(v)),
  z.number().nullable(),
);
const boolOrNull = z.preprocess((v) => {
  if (v === "true") return true;
  if (v === "false") return false;
  return null;
}, z.boolean().nullable());

const CoreSchema = z.object({
  birthDate: dateOrNull,
  marriageDate: dateOrNull,
  isHouseholder: boolOrNull,
  isFirstTimeBuyer: boolOrNull,
  noHomeSince: dateOrNull,
  subscriptionAccountOpenedAt: dateOrNull,
  subscriptionDepositMonths: intOrNull,
  subscriptionDepositTotal: longOrNull,
});

const RELATIONS: readonly HouseholdRelation[] = [
  "SPOUSE",
  "CHILD",
  "PARENT",
  "GRANDPARENT",
  "OTHER",
] as const;

const OWNERS: readonly WorkplaceOwner[] = ["SELF", "SPOUSE"] as const;

const MemberSchema = z.object({
  relation: z.enum(RELATIONS as unknown as [HouseholdRelation, ...HouseholdRelation[]]),
  birthDate: dateOrNull,
});

const WorkplaceSchema = z.object({
  owner: z.enum(OWNERS as unknown as [WorkplaceOwner, ...WorkplaceOwner[]]),
  label: z.string().nullable().optional(),
  address: z.string().min(1, "주소를 입력하세요"),
  arrivalTime: z.string().regex(/^\d{2}:\d{2}(:\d{2})?$/).default("09:00"),
});

const IncomeSchema = z.object({
  year: z.preprocess((v) => Number(v), z.number().int().min(2000).max(2100)),
  selfAmount: longOrNull,
  spouseAmount: longOrNull,
});

const AssetsSchema = z.object({
  netWorth: longOrNull,
  realEstate: longOrNull,
  monthlyDebt: longOrNull,
});

const PreferencesSchema = z.object({
  maxPurchasePrice: longOrNull,        // 매매 예산 (원)
  maxJeonsePrice: longOrNull,          // 전세 예산 (원)
  maxMonthlyRent: intOrNull,           // 월세 한도 (원)
  maxDepositForRent: longOrNull,       // 보증금 한도 (원)
  minRooms: intOrNull,                 // 최소 방 개수
  maxCommuteMinutes: intOrNull,        // 최대 통근 시간 (분)
  preferredSidos: z.array(z.string()).default([]),  // 선호 시도 풀네임
});

const SaveSchema = z.object({
  core: CoreSchema,
  members: z.array(MemberSchema),
  workplaces: z.array(WorkplaceSchema),
  incomes: z.array(IncomeSchema).optional(),
  assets: AssetsSchema.optional(),
  preferences: PreferencesSchema.optional(),
});

export type SaveProfileInput = z.input<typeof SaveSchema>;
export type SaveProfileResult = { ok: true } | { ok: false; error: string };

/**
 * VWorld로 주소→좌표 변환. 서버 액션 환경(Vercel/Railway)에서 호출되며,
 * Railway 싱가포르 리전에서는 막힐 수 있음. 실패하면 좌표 없이 저장.
 */
async function tryGeocode(
  address: string,
): Promise<{ latitude: number; longitude: number } | null> {
  const key = process.env.VWORLD_API_KEY;
  if (!key) return null;
  const cleaned = address
    .trim()
    .replace(/\s+(일원|일대|외)$/, "")
    .replace(/\([^)]*\)/g, "")
    .trim();
  for (const type of ["ROAD", "PARCEL"]) {
    try {
      const url = new URL("https://api.vworld.kr/req/address");
      url.searchParams.set("service", "address");
      url.searchParams.set("request", "getCoord");
      url.searchParams.set("format", "json");
      url.searchParams.set("type", type);
      url.searchParams.set("address", cleaned);
      url.searchParams.set("key", key);
      const res = await fetch(url, { signal: AbortSignal.timeout(8000) });
      if (!res.ok) continue;
      const data = (await res.json()) as {
        response?: { result?: { point?: { x: string; y: string } }; status?: string };
      };
      if (data.response?.status !== "OK") continue;
      const p = data.response.result?.point;
      if (!p) continue;
      return { latitude: Number(p.y), longitude: Number(p.x) };
    } catch {
      // try next type / fall through
    }
  }
  return null;
}

export async function saveProfile(input: SaveProfileInput): Promise<SaveProfileResult> {
  const parsed = SaveSchema.safeParse(input);
  if (!parsed.success) {
    return { ok: false, error: parsed.error.issues.map((i) => i.message).join(", ") };
  }

  const coreRes = await apiFetch("/api/v1/profile", {
    method: "PUT",
    body: JSON.stringify(parsed.data.core),
  });
  if (!coreRes.ok) return { ok: false, error: `프로필 저장 실패 (HTTP ${coreRes.status})` };

  const memberRes = await apiFetch("/api/v1/profile/household-members", {
    method: "PUT",
    body: JSON.stringify(parsed.data.members),
  });
  if (!memberRes.ok) return { ok: false, error: `가족 저장 실패 (HTTP ${memberRes.status})` };

  // workplaces — 주소 → 좌표(가능하면) 후 저장
  const wpPayload = await Promise.all(
    parsed.data.workplaces.map(async (w) => {
      const geo = await tryGeocode(w.address);
      return {
        owner: w.owner,
        label: w.label ?? null,
        address: w.address,
        latitude: geo?.latitude ?? null,
        longitude: geo?.longitude ?? null,
        arrivalTime: w.arrivalTime.length === 5 ? `${w.arrivalTime}:00` : w.arrivalTime,
      };
    }),
  );
  const wpRes = await apiFetch("/api/v1/profile/workplaces", {
    method: "PUT",
    body: JSON.stringify(wpPayload),
  });
  if (!wpRes.ok) return { ok: false, error: `직장 저장 실패 (HTTP ${wpRes.status})` };

  // incomes (대출 추정/매칭 자격용)
  if (parsed.data.incomes) {
    const incRes = await apiFetch("/api/v1/profile/incomes", {
      method: "PUT",
      body: JSON.stringify(parsed.data.incomes),
    });
    if (!incRes.ok) return { ok: false, error: `소득 저장 실패 (HTTP ${incRes.status})` };
  }

  // assets (자산 + 월 채무 — DSR 계산용)
  if (parsed.data.assets) {
    const aRes = await apiFetch("/api/v1/profile/assets", {
      method: "PUT",
      body: JSON.stringify(parsed.data.assets),
    });
    if (!aRes.ok) return { ok: false, error: `자산 저장 실패 (HTTP ${aRes.status})` };
  }

  // preferences (예산/방수/통근시간/선호지역)
  if (parsed.data.preferences) {
    const prefRes = await apiFetch("/api/v1/profile/preferences", {
      method: "PUT",
      body: JSON.stringify(parsed.data.preferences),
    });
    if (!prefRes.ok) return { ok: false, error: `선호 저장 실패 (HTTP ${prefRes.status})` };
  }

  revalidatePath("/profile");
  return { ok: true };
}

export async function setEmailNotifications(
  enabled: boolean,
): Promise<{ ok: true } | { ok: false; error: string }> {
  const res = await apiFetch("/api/v1/notifications/preferences", {
    method: "PUT",
    body: JSON.stringify({ emailEnabled: enabled }),
  });
  if (!res.ok) return { ok: false, error: `알림 설정 저장 실패 (HTTP ${res.status})` };
  revalidatePath("/profile");
  return { ok: true };
}
