"use server";

import { apiFetch } from "@/lib/api";
import type { ListingDetail } from "@/lib/api";

export async function fetchListing(id: number): Promise<ListingDetail | null> {
  const res = await apiFetch(`/api/v1/listings/${id}`);
  if (!res.ok) return null;
  return (await res.json()) as ListingDetail;
}

export interface ListingPatchPayload {
  name?: string | null;
  address?: string | null;
  sido?: string | null;
  sigungu?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  totalSupply?: number | null;
}

export type PatchResult = { ok: true } | { ok: false; error: string };

export async function patchListing(
  token: string,
  id: number,
  patch: ListingPatchPayload,
): Promise<PatchResult> {
  if (!token) return { ok: false, error: "토큰 없음" };
  const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
  try {
    const res = await fetch(`${apiBase}/api/v1/admin/listings/${id}`, {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
        "X-Admin-Token": token,
      },
      body: JSON.stringify(patch),
      cache: "no-store",
    });
    if (!res.ok) {
      const text = await res.text();
      return { ok: false, error: `HTTP ${res.status}: ${text.slice(0, 200)}` };
    }
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}
