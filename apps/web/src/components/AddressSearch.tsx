"use client";

import Script from "next/script";
import { useCallback, useState } from "react";

const DAUM_SCRIPT = "//t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";

declare global {
  interface Window {
    daum?: {
      Postcode: new (config: {
        oncomplete: (data: DaumAddressData) => void;
      }) => { open: () => void };
    };
  }
}

export interface DaumAddressData {
  address: string;
  addressType: "R" | "J";
  bname: string;
  buildingName: string;
  roadAddress: string;
  jibunAddress: string;
  sido: string;
  sigungu: string;
  zonecode: string;
}

export default function AddressSearch({
  value,
  onChange,
  placeholder = "주소를 검색하세요",
}: {
  value: string;
  onChange: (address: string, parts?: { sido?: string; sigungu?: string }) => void;
  placeholder?: string;
}) {
  const [scriptReady, setScriptReady] = useState(
    typeof window !== "undefined" && !!window.daum,
  );

  const open = useCallback(() => {
    if (!window.daum) return;
    new window.daum.Postcode({
      oncomplete: (data) => {
        // 도로명 우선, 없으면 지번
        const addr = data.roadAddress || data.jibunAddress || data.address;
        onChange(addr, { sido: data.sido, sigungu: data.sigungu });
      },
    }).open();
  }, [onChange]);

  return (
    <div className="flex gap-2">
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="input flex-1"
        readOnly
        onClick={scriptReady ? open : undefined}
      />
      <button
        type="button"
        onClick={open}
        disabled={!scriptReady}
        className="rounded border border-zinc-300 px-3 py-2 text-sm text-zinc-700 hover:bg-zinc-50 disabled:opacity-50"
      >
        주소 검색
      </button>
      <Script
        src={DAUM_SCRIPT}
        strategy="lazyOnload"
        onLoad={() => setScriptReady(true)}
        onReady={() => setScriptReady(true)}
      />
    </div>
  );
}
