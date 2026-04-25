import { auth } from "@/auth";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export async function apiFetch(path: string, init?: RequestInit): Promise<Response> {
  const session = await auth();
  const headers = new Headers(init?.headers);
  if (!headers.has("Content-Type") && init?.body) {
    headers.set("Content-Type", "application/json");
  }
  if (session?.accessToken) {
    headers.set("Authorization", `Bearer ${session.accessToken}`);
  }
  return fetch(`${API_BASE}${path}`, { ...init, headers, cache: "no-store" });
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

export interface FullProfile {
  userId: number;
  core: ProfileCore;
  householdMembers: HouseholdMember[];
  incomes: Array<{ year: number; selfAmount?: number; spouseAmount?: number }>;
  assets: { netWorth?: number; realEstate?: number } | null;
  residences: unknown[];
  workplaces: Workplace[];
  preferences: unknown | null;
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

export interface ScoreResp {
  total: number;
  max: number;
  breakdown: {
    noHomePeriod: { points: number; max: number };
    dependents: { points: number; max: number };
    accountAge: { points: number; max: number };
  };
  notes: string[];
}
