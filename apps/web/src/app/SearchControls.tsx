"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";

/**
 * 텍스트 검색 + 최대 분양가(억원) + 정렬 셀렉트.
 * URL 쿼리 파라미터에 반영 — 서버 컴포넌트가 다시 fetch.
 *
 * sido / types 는 칩으로 별도 관리되므로 props 로 받아서 form 에 hidden 포함.
 */
export default function SearchControls({
  initialQ,
  initialMaxPriceEok,
  initialSort,
  sortOptions,
  sido,
  types,
}: {
  initialQ: string;
  initialMaxPriceEok: string;
  initialSort: string;
  sortOptions: Array<{ v: string; label: string }>;
  sido?: string;
  types: string[];
}) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [q, setQ] = useState(initialQ);
  const [maxPriceEok, setMaxPriceEok] = useState(initialMaxPriceEok);
  const [sort, setSort] = useState(initialSort);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    push({ q, maxPriceEok, sort });
  };

  const push = (next: { q?: string; maxPriceEok?: string; sort?: string }) => {
    const u = new URLSearchParams();
    if (sido) u.set("sido", sido);
    for (const t of types) u.append("type", t);
    if (next.q?.trim()) u.set("q", next.q.trim());
    if (next.maxPriceEok?.trim()) u.set("maxPriceEok", next.maxPriceEok.trim());
    if (next.sort && next.sort !== "CLOSING") u.set("sort", next.sort);
    startTransition(() => router.push(`/?${u.toString()}`));
  };

  return (
    <form onSubmit={submit} className="rounded-lg border border-zinc-200 bg-white p-3">
      <div className="flex flex-wrap items-end gap-2">
        <label className="flex-1 min-w-[200px]">
          <span className="text-xs text-zinc-500">검색</span>
          <input
            type="text"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="단지명/주소"
            className="mt-1 block w-full rounded border border-zinc-300 px-3 py-2 text-sm"
          />
        </label>
        <label className="w-32">
          <span className="text-xs text-zinc-500">예산 (억원 이하)</span>
          <input
            type="number"
            min={0}
            step="0.5"
            value={maxPriceEok}
            onChange={(e) => setMaxPriceEok(e.target.value)}
            placeholder="예: 8"
            className="mt-1 block w-full rounded border border-zinc-300 px-3 py-2 text-sm"
          />
        </label>
        <label className="w-40">
          <span className="text-xs text-zinc-500">정렬</span>
          <select
            value={sort}
            onChange={(e) => {
              setSort(e.target.value);
              push({ q, maxPriceEok, sort: e.target.value });
            }}
            className="mt-1 block w-full rounded border border-zinc-300 bg-white px-3 py-2 text-sm"
          >
            {sortOptions.map((o) => (
              <option key={o.v} value={o.v}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
        <button
          type="submit"
          disabled={pending}
          className="h-[38px] rounded bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {pending ? "검색 중..." : "검색"}
        </button>
        {(q || maxPriceEok || sort !== "CLOSING") && (
          <button
            type="button"
            onClick={() => {
              setQ("");
              setMaxPriceEok("");
              setSort("CLOSING");
              push({});
            }}
            className="h-[38px] rounded border border-zinc-300 bg-white px-3 text-sm text-zinc-600 hover:bg-zinc-50"
          >
            초기화
          </button>
        )}
      </div>
    </form>
  );
}
