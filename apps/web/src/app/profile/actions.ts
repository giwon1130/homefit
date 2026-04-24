"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";
import { apiFetch, type HouseholdRelation } from "@/lib/api";

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

const MemberSchema = z.object({
  relation: z.enum(RELATIONS as unknown as [HouseholdRelation, ...HouseholdRelation[]]),
  birthDate: dateOrNull,
});

const SaveSchema = z.object({
  core: CoreSchema,
  members: z.array(MemberSchema),
});

export type SaveProfileInput = z.input<typeof SaveSchema>;
export type SaveProfileResult = { ok: true } | { ok: false; error: string };

export async function saveProfile(input: SaveProfileInput): Promise<SaveProfileResult> {
  const parsed = SaveSchema.safeParse(input);
  if (!parsed.success) {
    return { ok: false, error: parsed.error.issues.map((i) => i.message).join(", ") };
  }

  const coreRes = await apiFetch("/api/v1/profile", {
    method: "PUT",
    body: JSON.stringify(parsed.data.core),
  });
  if (!coreRes.ok) {
    return { ok: false, error: `프로필 저장 실패 (HTTP ${coreRes.status})` };
  }

  const memberRes = await apiFetch("/api/v1/profile/household-members", {
    method: "PUT",
    body: JSON.stringify(parsed.data.members),
  });
  if (!memberRes.ok) {
    return { ok: false, error: `가족 저장 실패 (HTTP ${memberRes.status})` };
  }

  revalidatePath("/profile");
  return { ok: true };
}
