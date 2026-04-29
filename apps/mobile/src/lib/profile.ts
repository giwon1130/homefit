import { apiFetch } from "./api";
import type {
  Assets,
  FullProfile,
  HouseholdMember,
  Income,
  Preferences,
  ProfileCore,
  Workplace,
} from "./api";

export async function fetchProfile(): Promise<FullProfile | null> {
  const res = await apiFetch("/api/v1/profile");
  if (!res.ok) return null;
  return (await res.json()) as FullProfile;
}

export interface SaveProfilePayload {
  core: ProfileCore;
  members: HouseholdMember[];
  workplaces: Array<Omit<Workplace, "latitude" | "longitude"> & { latitude?: number | null; longitude?: number | null }>;
  incomes: Income[];
  assets: Assets | null;
  preferences: Preferences | null;
}

export type SaveResult = { ok: true } | { ok: false; error: string };

/**
 * 프로필 전체 저장. 웹 server action 과 달리 모바일에서는 직접 백엔드 호출.
 * 좌표(geocode)는 서버 쪽에서 채우거나, 입력 안 됐으면 null 로 보냄.
 */
export async function saveProfile(payload: SaveProfilePayload): Promise<SaveResult> {
  try {
    const core = await apiFetch("/api/v1/profile", {
      method: "PUT",
      body: JSON.stringify(payload.core),
    });
    if (!core.ok) return { ok: false, error: `프로필 저장 실패 (${core.status})` };

    const m = await apiFetch("/api/v1/profile/household-members", {
      method: "PUT",
      body: JSON.stringify(payload.members),
    });
    if (!m.ok) return { ok: false, error: `가족 저장 실패 (${m.status})` };

    const w = await apiFetch("/api/v1/profile/workplaces", {
      method: "PUT",
      body: JSON.stringify(
        payload.workplaces.map((wp) => ({
          owner: wp.owner,
          label: wp.label ?? null,
          address: wp.address,
          latitude: wp.latitude ?? null,
          longitude: wp.longitude ?? null,
          arrivalTime:
            wp.arrivalTime && wp.arrivalTime.length === 5
              ? `${wp.arrivalTime}:00`
              : wp.arrivalTime ?? "09:00:00",
        })),
      ),
    });
    if (!w.ok) return { ok: false, error: `직장 저장 실패 (${w.status})` };

    if (payload.incomes.length > 0) {
      const inc = await apiFetch("/api/v1/profile/incomes", {
        method: "PUT",
        body: JSON.stringify(payload.incomes),
      });
      if (!inc.ok) return { ok: false, error: `소득 저장 실패 (${inc.status})` };
    }

    if (payload.assets) {
      const a = await apiFetch("/api/v1/profile/assets", {
        method: "PUT",
        body: JSON.stringify(payload.assets),
      });
      if (!a.ok) return { ok: false, error: `자산 저장 실패 (${a.status})` };
    }

    if (payload.preferences) {
      const p = await apiFetch("/api/v1/profile/preferences", {
        method: "PUT",
        body: JSON.stringify(payload.preferences),
      });
      if (!p.ok) return { ok: false, error: `선호 저장 실패 (${p.status})` };
    }

    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

// ---- conversion helpers (만원 / 억원 → 원) ----

export function eokToWon(s: string): number | null {
  const n = Number(s);
  return Number.isFinite(n) && n > 0 ? Math.round(n * 100_000_000) : null;
}

export function manToWon(s: string): number | null {
  const n = Number(s);
  return Number.isFinite(n) && n > 0 ? Math.round(n * 10_000) : null;
}

export function wonToEok(n?: number | null): string {
  return n != null && n > 0 ? (n / 100_000_000).toString() : "";
}

export function wonToMan(n?: number | null): string {
  return n != null && n > 0 ? Math.round(n / 10_000).toString() : "";
}
