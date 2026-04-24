// API 타입은 Phase 1에서 OpenAPI 스펙으로부터 생성됩니다.
// 현재는 공유 enum / 상수만 정의.

export const LISTING_TYPES = [
  "PRIVATE_SALE",
  "PUBLIC_SALE",
  "HAPPY_HOUSE",
  "NEWLYWED_HOPE",
  "PURCHASE_RENTAL",
  "JEONSE_RENTAL",
  "NATIONAL_RENTAL",
] as const;
export type ListingType = (typeof LISTING_TYPES)[number];

export const SUPPLY_TYPES = [
  "FIRST_TIME",
  "NEWLYWED",
  "MULTI_CHILD",
  "GENERAL",
] as const;
export type SupplyType = (typeof SUPPLY_TYPES)[number];
