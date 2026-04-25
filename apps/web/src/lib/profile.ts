import type { FullProfile } from "./api";

/**
 * 프로필이 "비어있는지" 판정.
 * 코어 / 가족 / 직장 / 선호 중 어느 한 곳이라도 채워져있으면 false.
 * 온보딩 분기 + /match 배너 노출 판정에 사용.
 */
export function isProfileEmpty(p: FullProfile): boolean {
  const c = p.core;
  const coreFilled =
    c.birthDate ||
    c.marriageDate ||
    c.isHouseholder !== null ||
    c.isFirstTimeBuyer !== null ||
    c.noHomeSince ||
    c.subscriptionAccountOpenedAt;
  if (coreFilled) return false;
  if (p.householdMembers.length > 0) return false;
  if (p.workplaces.length > 0) return false;
  if (p.preferences) return false;
  return true;
}
