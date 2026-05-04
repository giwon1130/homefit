import { auth, signOut } from "@/auth";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

/**
 * 서버 컴포넌트/액션에서만 사용. 401 받으면 세션 만료로 간주해 자동 로그아웃 후 /login 리다이렉트.
 *  - signIn 안 한 익명 호출에도 401 가능 — session 이 있을 때만 로그아웃 처리
 *  - 호출자가 명시적으로 401 처리하고 싶으면 `allow401: true` 옵션
 */
export async function apiFetch(
  path: string,
  init?: RequestInit & { allow401?: boolean },
): Promise<Response> {
  const session = await auth();
  const headers = new Headers(init?.headers);
  if (!headers.has("Content-Type") && init?.body) {
    headers.set("Content-Type", "application/json");
  }
  if (session?.accessToken) {
    headers.set("Authorization", `Bearer ${session.accessToken}`);
  }
  const res = await fetch(`${API_BASE}${path}`, { ...init, headers, cache: "no-store" });

  // 세션이 있는데 401 → 백엔드 토큰 만료. 무음 로그아웃 + /login 리다이렉트.
  // signOut() 은 NextAuth v5 서버사이드 — CSRF 확인 페이지 안 보여주고 쿠키 정리.
  if (res.status === 401 && session?.accessToken && !init?.allow401) {
    await signOut({ redirectTo: "/login?error=session_expired" });
  }
  return res;
}

// ---- typed response models (subset) ----

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
  latitude?: number | null;
  longitude?: number | null;
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

export interface UnitResp {
  id: number;
  modelNo?: string;
  unitType?: string;
  sizeM2?: number;
  supplyCount?: number;
  priceMaxKrw?: number;
}

export interface ListingDetail extends ListingSummary {
  winnerAnnouncementDate?: string;
  contractStartDate?: string;
  contractEndDate?: string;
  units: UnitResp[];
  polygonGeoJson?: GeoJSON.FeatureCollection | null;
}

export type SupplyType = "GENERAL" | "FIRST_TIME" | "NEWLYWED" | "MULTI_CHILD";

export interface EligibilityDetailResp {
  supplyType: SupplyType;
  eligible: boolean;
  reasons: string[];
}

export interface EligibilityResp {
  eligibleSupplyTypes: SupplyType[];
  bestSupplyType: SupplyType | null;
  details: EligibilityDetailResp[];
}

// ---- loan ----

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

// ---- profile ----

export type HouseholdRelation =
  | "SPOUSE"
  | "CHILD"
  | "PARENT"
  | "GRANDPARENT"
  | "OTHER";

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

export type WorkplaceOwner = "SELF" | "SPOUSE";

export interface Workplace {
  owner: WorkplaceOwner;
  label?: string | null;
  address: string;
  latitude?: number | null;
  longitude?: number | null;
  arrivalTime?: string;
}

export interface Preferences {
  maxPurchasePrice?: number | null;
  maxJeonsePrice?: number | null;
  maxMonthlyRent?: number | null;
  maxDepositForRent?: number | null;
  minSizeM2?: number | null;
  maxSizeM2?: number | null;
  minRooms?: number | null;
  maxCommuteMinutes?: number | null;
  preferredSidos?: string[];
}

export interface Income {
  year: number;
  selfAmount?: number | null;
  spouseAmount?: number | null;
}

export interface Assets {
  netWorth?: number | null;
  realEstate?: number | null;
  monthlyDebt?: number | null;     // 기존 채무 월 상환액 (원)
}

export interface FullProfile {
  userId: number;
  core: ProfileCore;
  householdMembers: HouseholdMember[];
  incomes: Income[];
  assets: Assets | null;
  residences: unknown[];
  workplaces: Workplace[];
  preferences: Preferences | null;
  housingHistory: unknown[];
}

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

export interface ScoreItem {
  points: number;
  max: number;
  reason: string;
}

export interface ScoreResp {
  total: number;
  max: number;
  breakdown: {
    noHomePeriod: ScoreItem;
    dependents: ScoreItem;
    accountAge: ScoreItem;
  };
  notes: string[];
}
