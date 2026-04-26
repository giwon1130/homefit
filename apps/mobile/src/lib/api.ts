import Constants from "expo-constants";
import { getAccessToken } from "./auth";

const API_BASE: string =
  (Constants.expoConfig?.extra as { apiBaseUrl?: string } | undefined)?.apiBaseUrl ??
  process.env.EXPO_PUBLIC_API_BASE_URL ??
  "https://api-production-1d45.up.railway.app";

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const token = await getAccessToken();
  const headers = new Headers(init.headers);
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (token) headers.set("Authorization", `Bearer ${token}`);
  return fetch(`${API_BASE}${path}`, { ...init, headers });
}

// ===== Types (web 와 동일) =====

export type ListingType =
  | "PRIVATE_SALE"
  | "PUBLIC_SALE"
  | "NEWLYWED_HOPE"
  | "HAPPY_HOUSE"
  | "PURCHASE_RENTAL"
  | "JEONSE_RENTAL"
  | "NATIONAL_RENTAL"
  | "OTHER";

export interface ListingSummary {
  id: number;
  name: string;
  listingType: ListingType;
  sido?: string;
  sigungu?: string;
  address?: string;
  developer?: string;
  applicationStart?: string;
  applicationEnd?: string;
  announcementDate?: string;
  moveInDate?: string;
  totalSupply?: number;
  documentUrl?: string;
}

export interface ListingPage {
  content: ListingSummary[];
  page: number;
  size: number;
  total: number;
}

export interface ListingDetail extends ListingSummary {
  latitude?: number | null;
  longitude?: number | null;
  winnerAnnouncementDate?: string;
  units: Array<{
    id: number;
    unitType?: string;
    sizeM2?: number;
    supplyCount?: number;
    priceMaxKrw?: number;
    depositAmount?: number;
    monthlyRent?: number;
  }>;
}

export type SupplyType = "GENERAL" | "FIRST_TIME" | "NEWLYWED" | "MULTI_CHILD";

export const SUPPLY_TYPE_LABEL: Record<SupplyType, string> = {
  GENERAL: "일반공급",
  FIRST_TIME: "생애최초",
  NEWLYWED: "신혼부부",
  MULTI_CHILD: "다자녀",
};

export interface EligibilityResp {
  eligibleSupplyTypes: SupplyType[];
  bestSupplyType: SupplyType | null;
  details: Array<{
    supplyType: SupplyType;
    eligible: boolean;
    reasons: string[];
  }>;
}

export interface LoanProduct {
  name: string;
  eligible: boolean;
  limitKrw: number | null;
  reasons: string[];
}

export interface LoanEstimateResp {
  listingPriceKrw: number;
  annualIncomeKrw: number | null;
  products: LoanProduct[];
  recommended: LoanProduct | null;
  selfFundingKrw: number | null;
  notes: string[];
}

export const LISTING_TYPE_LABEL: Record<ListingType, string> = {
  PRIVATE_SALE: "민영분양",
  PUBLIC_SALE: "공공분양",
  NEWLYWED_HOPE: "신혼희망타운",
  HAPPY_HOUSE: "행복주택",
  PURCHASE_RENTAL: "매입임대",
  JEONSE_RENTAL: "전세임대",
  NATIONAL_RENTAL: "국민임대",
  OTHER: "기타",
};

export interface MeResp {
  id: number;
  email: string;
  displayName?: string;
  profileImageUrl?: string;
}
