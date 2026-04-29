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

// ===== profile =====

export type HouseholdRelation = "SPOUSE" | "CHILD" | "PARENT" | "GRANDPARENT" | "OTHER";
export type WorkplaceOwner = "SELF" | "SPOUSE";

export interface ProfileCore {
  birthDate?: string | null;
  marriageDate?: string | null;
  isHouseholder?: boolean | null;
  isFirstTimeBuyer?: boolean | null;
  noHomeSince?: string | null;
  subscriptionAccountOpenedAt?: string | null;
  subscriptionDepositMonths?: number | null;
  subscriptionDepositTotal?: number | null;
}

export interface HouseholdMember {
  relation: HouseholdRelation;
  birthDate?: string | null;
}

export interface Workplace {
  owner: WorkplaceOwner;
  label?: string | null;
  address: string;
  latitude?: number | null;
  longitude?: number | null;
  arrivalTime?: string;
}

export interface Income {
  year: number;
  selfAmount?: number | null;
  spouseAmount?: number | null;
}

export interface Assets {
  netWorth?: number | null;
  realEstate?: number | null;
  monthlyDebt?: number | null;
}

export interface Preferences {
  maxPurchasePrice?: number | null;
  maxJeonsePrice?: number | null;
  maxMonthlyRent?: number | null;
  maxDepositForRent?: number | null;
  minRooms?: number | null;
  maxCommuteMinutes?: number | null;
  preferredSidos?: string[];
}

export interface FullProfile {
  userId: number;
  core: ProfileCore;
  householdMembers: HouseholdMember[];
  incomes: Income[];
  assets: Assets | null;
  workplaces: Workplace[];
  preferences: Preferences | null;
  residences: unknown[];
  housingHistory: unknown[];
}

// ===== match =====

export interface MatchingScore {
  total: number;
  max: number;
  eligibility: number;
  budget: number;
  region: number;
  commute: number;
  commuteMinutes: number | null;
  bestSupplyType: SupplyType | null;
  notes: string[];
}

export interface MatchedListing {
  listing: ListingSummary;
  score: MatchingScore;
}

export interface MatchedListingPage {
  content: MatchedListing[];
  page: number;
  size: number;
  total: number;
}

// ===== UI constants =====

export const SIDO_OPTIONS = [
  { short: "서울", full: "서울특별시" },
  { short: "경기", full: "경기도" },
  { short: "인천", full: "인천광역시" },
  { short: "부산", full: "부산광역시" },
  { short: "대구", full: "대구광역시" },
  { short: "대전", full: "대전광역시" },
  { short: "광주", full: "광주광역시" },
  { short: "울산", full: "울산광역시" },
  { short: "세종", full: "세종특별자치시" },
  { short: "강원", full: "강원특별자치도" },
  { short: "충북", full: "충청북도" },
  { short: "충남", full: "충청남도" },
  { short: "전북", full: "전북특별자치도" },
  { short: "전남", full: "전라남도" },
  { short: "경북", full: "경상북도" },
  { short: "경남", full: "경상남도" },
  { short: "제주", full: "제주특별자치도" },
] as const;

export const TYPE_OPTIONS: ListingType[] = [
  "PRIVATE_SALE",
  "PUBLIC_SALE",
  "NEWLYWED_HOPE",
  "HAPPY_HOUSE",
  "PURCHASE_RENTAL",
  "JEONSE_RENTAL",
  "NATIONAL_RENTAL",
];
