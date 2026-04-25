"use client";

import { useState, useTransition } from "react";
import type {
  HouseholdMember,
  HouseholdRelation,
  ProfileCore,
  Workplace,
  WorkplaceOwner,
} from "@/lib/api";
import { saveProfile } from "./actions";

const RELATION_LABEL: Record<HouseholdRelation, string> = {
  SPOUSE: "배우자",
  CHILD: "자녀",
  PARENT: "부모",
  GRANDPARENT: "조부모",
  OTHER: "기타",
};

type CoreFormState = {
  birthDate: string;
  marriageDate: string;
  isHouseholder: "true" | "false" | "";
  isFirstTimeBuyer: "true" | "false" | "";
  noHomeSince: string;
  subscriptionAccountOpenedAt: string;
  subscriptionDepositMonths: string;
  subscriptionDepositTotal: string;
};

type MemberFormState = {
  relation: HouseholdRelation;
  birthDate: string;
};

type WorkplaceFormState = {
  owner: WorkplaceOwner;
  label: string;
  address: string;
  arrivalTime: string;
};

const OWNER_LABEL: Record<WorkplaceOwner, string> = { SELF: "본인", SPOUSE: "배우자" };

function toState(core: ProfileCore): CoreFormState {
  return {
    birthDate: core.birthDate ?? "",
    marriageDate: core.marriageDate ?? "",
    isHouseholder:
      core.isHouseholder == null ? "" : core.isHouseholder ? "true" : "false",
    isFirstTimeBuyer:
      core.isFirstTimeBuyer == null ? "" : core.isFirstTimeBuyer ? "true" : "false",
    noHomeSince: core.noHomeSince ?? "",
    subscriptionAccountOpenedAt: core.subscriptionAccountOpenedAt ?? "",
    subscriptionDepositMonths: core.subscriptionDepositMonths?.toString() ?? "",
    subscriptionDepositTotal: core.subscriptionDepositTotal?.toString() ?? "",
  };
}

function membersToState(list: HouseholdMember[]): MemberFormState[] {
  return list.map((m) => ({
    relation: m.relation,
    birthDate: m.birthDate ?? "",
  }));
}

function workplacesToState(list: Workplace[]): WorkplaceFormState[] {
  return list.map((w) => ({
    owner: w.owner,
    label: w.label ?? "",
    address: w.address,
    arrivalTime: (w.arrivalTime ?? "09:00:00").slice(0, 5),
  }));
}

export default function ProfileForm({
  initialCore,
  initialMembers,
  initialWorkplaces,
}: {
  initialCore: ProfileCore;
  initialMembers: HouseholdMember[];
  initialWorkplaces: Workplace[];
}) {
  const [core, setCore] = useState<CoreFormState>(toState(initialCore));
  const [members, setMembers] = useState<MemberFormState[]>(membersToState(initialMembers));
  const [workplaces, setWorkplaces] = useState<WorkplaceFormState[]>(
    workplacesToState(initialWorkplaces),
  );
  const [pending, startTransition] = useTransition();
  const [message, setMessage] = useState<{ kind: "ok" | "err"; text: string } | null>(null);

  const updateCore = <K extends keyof CoreFormState>(key: K, value: CoreFormState[K]) => {
    setCore((c) => ({ ...c, [key]: value }));
  };

  const addMember = () => setMembers((ms) => [...ms, { relation: "CHILD", birthDate: "" }]);
  const removeMember = (idx: number) => setMembers((ms) => ms.filter((_, i) => i !== idx));
  const updateMember = (idx: number, patch: Partial<MemberFormState>) =>
    setMembers((ms) => ms.map((m, i) => (i === idx ? { ...m, ...patch } : m)));

  const addWorkplace = () =>
    setWorkplaces((ws) => [
      ...ws,
      { owner: "SELF", label: "", address: "", arrivalTime: "09:00" },
    ]);
  const removeWorkplace = (idx: number) =>
    setWorkplaces((ws) => ws.filter((_, i) => i !== idx));
  const updateWorkplace = (idx: number, patch: Partial<WorkplaceFormState>) =>
    setWorkplaces((ws) => ws.map((w, i) => (i === idx ? { ...w, ...patch } : w)));

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setMessage(null);
    startTransition(async () => {
      const result = await saveProfile({
        core: {
          birthDate: core.birthDate,
          marriageDate: core.marriageDate,
          isHouseholder: core.isHouseholder,
          isFirstTimeBuyer: core.isFirstTimeBuyer,
          noHomeSince: core.noHomeSince,
          subscriptionAccountOpenedAt: core.subscriptionAccountOpenedAt,
          subscriptionDepositMonths: core.subscriptionDepositMonths,
          subscriptionDepositTotal: core.subscriptionDepositTotal,
        },
        members: members.map((m) => ({ relation: m.relation, birthDate: m.birthDate })),
        workplaces: workplaces
          .filter((w) => w.address.trim().length > 0)
          .map((w) => ({
            owner: w.owner,
            label: w.label || null,
            address: w.address,
            arrivalTime: w.arrivalTime,
          })),
      });
      if (result.ok) {
        setMessage({ kind: "ok", text: "저장 완료" });
      } else {
        setMessage({ kind: "err", text: result.error });
      }
    });
  };

  return (
    <form onSubmit={onSubmit} className="space-y-6">
      <Section title="기본 정보">
        <Field label="생년월일">
          <input
            type="date"
            value={core.birthDate}
            onChange={(e) => updateCore("birthDate", e.target.value)}
            className="input"
          />
        </Field>
        <Field label="혼인일">
          <input
            type="date"
            value={core.marriageDate}
            onChange={(e) => updateCore("marriageDate", e.target.value)}
            className="input"
          />
        </Field>
        <Field label="세대주">
          <Triple
            value={core.isHouseholder}
            onChange={(v) => updateCore("isHouseholder", v)}
            yesLabel="예"
            noLabel="아니요"
          />
        </Field>
        <Field label="생애최초">
          <Triple
            value={core.isFirstTimeBuyer}
            onChange={(v) => updateCore("isFirstTimeBuyer", v)}
            yesLabel="해당"
            noLabel="아님"
          />
        </Field>
        <Field label="무주택 기산일">
          <input
            type="date"
            value={core.noHomeSince}
            onChange={(e) => updateCore("noHomeSince", e.target.value)}
            className="input"
          />
        </Field>
      </Section>

      <Section title="청약통장">
        <Field label="가입일">
          <input
            type="date"
            value={core.subscriptionAccountOpenedAt}
            onChange={(e) => updateCore("subscriptionAccountOpenedAt", e.target.value)}
            className="input"
          />
        </Field>
        <Field label="납입 횟수">
          <input
            type="number"
            min={0}
            value={core.subscriptionDepositMonths}
            onChange={(e) => updateCore("subscriptionDepositMonths", e.target.value)}
            className="input"
          />
        </Field>
        <Field label="납입 총액 (원)">
          <input
            type="number"
            min={0}
            step={10000}
            value={core.subscriptionDepositTotal}
            onChange={(e) => updateCore("subscriptionDepositTotal", e.target.value)}
            className="input"
          />
        </Field>
      </Section>

      <Section title="직장 (통근시간 계산용)">
        <div className="space-y-2 sm:col-span-2">
          {workplaces.length === 0 && (
            <p className="text-sm text-zinc-500">
              없음. 직장 추가하면 매칭 화면 통근 점수가 활성화돼요.
            </p>
          )}
          {workplaces.map((w, idx) => (
            <div key={idx} className="rounded border border-zinc-200 bg-white p-3 space-y-2">
              <div className="flex flex-wrap items-end gap-2">
                <div className="w-24">
                  <label className="text-xs text-zinc-500">소유자</label>
                  <select
                    value={w.owner}
                    onChange={(e) =>
                      updateWorkplace(idx, { owner: e.target.value as WorkplaceOwner })
                    }
                    className="input"
                  >
                    {(Object.keys(OWNER_LABEL) as WorkplaceOwner[]).map((o) => (
                      <option key={o} value={o}>
                        {OWNER_LABEL[o]}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="w-28">
                  <label className="text-xs text-zinc-500">출근시각</label>
                  <input
                    type="time"
                    value={w.arrivalTime}
                    onChange={(e) => updateWorkplace(idx, { arrivalTime: e.target.value })}
                    className="input"
                  />
                </div>
                <div className="flex-1 min-w-[140px]">
                  <label className="text-xs text-zinc-500">레이블 (선택)</label>
                  <input
                    type="text"
                    value={w.label}
                    placeholder="회사명"
                    onChange={(e) => updateWorkplace(idx, { label: e.target.value })}
                    className="input"
                  />
                </div>
                <button
                  type="button"
                  onClick={() => removeWorkplace(idx)}
                  className="rounded border border-zinc-300 px-3 py-2 text-sm text-zinc-700 hover:bg-zinc-50"
                >
                  삭제
                </button>
              </div>
              <div>
                <label className="text-xs text-zinc-500">주소</label>
                <input
                  type="text"
                  value={w.address}
                  onChange={(e) => updateWorkplace(idx, { address: e.target.value })}
                  placeholder="서울특별시 강남구 테헤란로 123"
                  className="input"
                />
              </div>
            </div>
          ))}
          <button
            type="button"
            onClick={addWorkplace}
            className="rounded border border-dashed border-zinc-400 px-3 py-2 text-sm text-zinc-700 hover:bg-zinc-50"
          >
            + 직장 추가
          </button>
        </div>
      </Section>

      <Section title="가족 구성 (본인 제외)">
        <div className="space-y-2">
          {members.length === 0 && (
            <p className="text-sm text-zinc-500">없음. + 버튼으로 추가하세요.</p>
          )}
          {members.map((m, idx) => (
            <div key={idx} className="flex items-end gap-2">
              <div className="flex-1">
                <label className="text-xs text-zinc-500">관계</label>
                <select
                  value={m.relation}
                  onChange={(e) =>
                    updateMember(idx, { relation: e.target.value as HouseholdRelation })
                  }
                  className="input"
                >
                  {(Object.keys(RELATION_LABEL) as HouseholdRelation[]).map((r) => (
                    <option key={r} value={r}>
                      {RELATION_LABEL[r]}
                    </option>
                  ))}
                </select>
              </div>
              <div className="flex-1">
                <label className="text-xs text-zinc-500">생년월일</label>
                <input
                  type="date"
                  value={m.birthDate}
                  onChange={(e) => updateMember(idx, { birthDate: e.target.value })}
                  className="input"
                />
              </div>
              <button
                type="button"
                onClick={() => removeMember(idx)}
                className="rounded border border-zinc-300 px-3 py-2 text-sm text-zinc-700 hover:bg-zinc-50"
              >
                삭제
              </button>
            </div>
          ))}
          <button
            type="button"
            onClick={addMember}
            className="rounded border border-dashed border-zinc-400 px-3 py-2 text-sm text-zinc-700 hover:bg-zinc-50"
          >
            + 가족 추가
          </button>
        </div>
      </Section>

      <div className="sticky bottom-0 -mx-4 flex items-center justify-end gap-3 border-t border-zinc-200 bg-white/80 px-4 py-3 backdrop-blur">
        {message && (
          <span
            className={
              message.kind === "ok"
                ? "text-sm text-green-700"
                : "text-sm text-red-700"
            }
          >
            {message.text}
          </span>
        )}
        <button
          type="submit"
          disabled={pending}
          className="rounded bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {pending ? "저장 중..." : "저장"}
        </button>
      </div>

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
        :global(.input:focus) {
          outline: 2px solid #2563eb;
          outline-offset: -1px;
          border-color: transparent;
        }
      `}</style>
    </form>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-3">
      <h2 className="font-semibold">{title}</h2>
      <div className="grid gap-3 sm:grid-cols-2">{children}</div>
    </section>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="text-xs text-zinc-500">{label}</span>
      <div className="mt-1">{children}</div>
    </label>
  );
}

function Triple({
  value,
  onChange,
  yesLabel,
  noLabel,
}: {
  value: "true" | "false" | "";
  onChange: (v: "true" | "false" | "") => void;
  yesLabel: string;
  noLabel: string;
}) {
  return (
    <div className="flex gap-2">
      {[
        { v: "true" as const, label: yesLabel },
        { v: "false" as const, label: noLabel },
        { v: "" as const, label: "미지정" },
      ].map((opt) => (
        <button
          key={opt.v}
          type="button"
          onClick={() => onChange(opt.v)}
          className={
            value === opt.v
              ? "flex-1 rounded border border-blue-600 bg-blue-50 px-3 py-2 text-sm text-blue-700"
              : "flex-1 rounded border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-700 hover:bg-zinc-50"
          }
        >
          {opt.label}
        </button>
      ))}
    </div>
  );
}
