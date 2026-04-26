"use client";

import { useEffect, useState, useTransition } from "react";
import type { ListingDetail } from "@/lib/api";
import { fetchListing, patchListing } from "./actions";

interface FormState {
  name: string;
  address: string;
  sido: string;
  sigungu: string;
  latitude: string;
  longitude: string;
  totalSupply: string;
}

const TOKEN_KEY = "homefit.adminToken";

export default function AdminCurationForm() {
  const [token, setToken] = useState("");
  const [idInput, setIdInput] = useState("");
  const [listing, setListing] = useState<ListingDetail | null>(null);
  const [form, setForm] = useState<FormState | null>(null);
  const [pending, startTransition] = useTransition();
  const [message, setMessage] = useState<{ kind: "ok" | "err"; text: string } | null>(null);

  useEffect(() => {
    const saved = typeof window !== "undefined" ? sessionStorage.getItem(TOKEN_KEY) : null;
    if (saved) setToken(saved);
  }, []);

  const saveToken = (v: string) => {
    setToken(v);
    if (typeof window !== "undefined") sessionStorage.setItem(TOKEN_KEY, v);
  };

  const loadListing = () => {
    setMessage(null);
    const id = Number(idInput);
    if (!Number.isFinite(id) || id <= 0) {
      setMessage({ kind: "err", text: "유효한 ID 를 입력하세요" });
      return;
    }
    startTransition(async () => {
      const data = await fetchListing(id);
      if (!data) {
        setMessage({ kind: "err", text: `listing ${id} 조회 실패` });
        setListing(null);
        setForm(null);
        return;
      }
      setListing(data);
      setForm({
        name: data.name ?? "",
        address: data.address ?? "",
        sido: data.sido ?? "",
        sigungu: data.sigungu ?? "",
        latitude: data.latitude?.toString() ?? "",
        longitude: data.longitude?.toString() ?? "",
        totalSupply: data.totalSupply?.toString() ?? "",
      });
    });
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form || !listing) return;
    if (!token) {
      setMessage({ kind: "err", text: "관리자 토큰을 먼저 입력하세요" });
      return;
    }
    setMessage(null);
    const num = (s: string) => (s.trim() === "" ? null : Number(s));
    const payload = {
      name: form.name.trim() || null,
      address: form.address.trim() || null,
      sido: form.sido.trim() || null,
      sigungu: form.sigungu.trim() || null,
      latitude: num(form.latitude),
      longitude: num(form.longitude),
      totalSupply: num(form.totalSupply),
    };
    startTransition(async () => {
      const r = await patchListing(token, listing.id, payload);
      if (r.ok) {
        setMessage({ kind: "ok", text: "저장 완료" });
        // refresh
        const data = await fetchListing(listing.id);
        if (data) setListing(data);
      } else {
        setMessage({ kind: "err", text: r.error });
      }
    });
  };

  return (
    <div className="space-y-4">
      <section className="rounded-lg border border-zinc-200 bg-white p-4 space-y-2">
        <label className="block">
          <span className="text-xs text-zinc-500">관리자 토큰 (X-Admin-Token, 세션 저장)</span>
          <input
            type="password"
            value={token}
            onChange={(e) => saveToken(e.target.value)}
            className="input mt-1"
            placeholder="ADMIN_TOKEN 값"
          />
        </label>
      </section>

      <section className="rounded-lg border border-zinc-200 bg-white p-4 space-y-2">
        <label className="block">
          <span className="text-xs text-zinc-500">단지 ID</span>
          <div className="mt-1 flex gap-2">
            <input
              type="number"
              value={idInput}
              onChange={(e) => setIdInput(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && loadListing()}
              className="input flex-1"
              placeholder="예: 1158"
            />
            <button
              type="button"
              onClick={loadListing}
              disabled={pending}
              className="rounded border border-zinc-300 px-4 py-2 text-sm text-zinc-700 hover:bg-zinc-50 disabled:opacity-50"
            >
              조회
            </button>
          </div>
        </label>
      </section>

      {form && listing && (
        <form
          onSubmit={submit}
          className="rounded-lg border border-zinc-200 bg-white p-4 space-y-3"
        >
          <div className="text-xs text-zinc-500">
            ID: {listing.id} · {listing.listingType}
          </div>
          <Field label="단지명" value={form.name} onChange={(v) => setForm({ ...form, name: v })} />
          <Field label="주소" value={form.address} onChange={(v) => setForm({ ...form, address: v })} />
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="시도 (예: 서울특별시)" value={form.sido} onChange={(v) => setForm({ ...form, sido: v })} />
            <Field label="시군구 (예: 강남구)" value={form.sigungu} onChange={(v) => setForm({ ...form, sigungu: v })} />
            <Field label="위도 (latitude)" value={form.latitude} onChange={(v) => setForm({ ...form, latitude: v })} />
            <Field label="경도 (longitude)" value={form.longitude} onChange={(v) => setForm({ ...form, longitude: v })} />
            <Field label="총 공급세대" value={form.totalSupply} onChange={(v) => setForm({ ...form, totalSupply: v })} />
          </div>
          <div className="flex items-center justify-between">
            {message && (
              <span className={message.kind === "ok" ? "text-sm text-green-700" : "text-sm text-red-700"}>
                {message.text}
              </span>
            )}
            <button
              type="submit"
              disabled={pending}
              className="ml-auto rounded bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {pending ? "저장 중..." : "저장"}
            </button>
          </div>
        </form>
      )}

      <style jsx>{`
        :global(.input) {
          display: block;
          width: 100%;
          border-radius: 0.375rem;
          border: 1px solid #e4e4e7;
          padding: 0.5rem 0.75rem;
          font-size: 0.875rem;
          background: white;
        }
      `}</style>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <label className="block">
      <span className="text-xs text-zinc-500">{label}</span>
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="input mt-1"
      />
    </label>
  );
}
