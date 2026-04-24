# API Design — homefit

Base URL: `/api/v1`
스펙은 springdoc-openapi가 자동 생성 (`/v3/api-docs`). 여기서는 합의된 엔드포인트와 컨벤션만 기술.

## 1. 컨벤션

- **인증:** `Authorization: Bearer <JWT>` (Web은 httpOnly cookie 방식도 허용)
- **페이징:** `?page=0&size=20` (Spring Data 관례)
- **에러 포맷:**
```json
{ "code": "LISTING_NOT_FOUND", "message": "해당 청약을 찾을 수 없습니다.", "detail": {} }
```
- **날짜/시간:** ISO 8601 + `Asia/Seoul` 오프셋 (`2026-04-24T09:00:00+09:00`)
- **금액:** 단위는 항상 **원** (BIGINT), 클라에서 포맷팅

## 2. 엔드포인트 목록

### 2.1 Auth

| Method | Path | 설명 |
|---|---|---|
| POST | `/auth/google` | `{ idToken }` → JWT 발급 |
| POST | `/auth/kakao` | `{ code, redirectUri }` → JWT 발급 |
| POST | `/auth/refresh` | refresh token → 신규 access |
| POST | `/auth/logout` | refresh revoke |
| GET  | `/auth/me` | 현재 유저 요약 |

### 2.2 Profile

| Method | Path |
|---|---|
| GET  | `/profile` |
| PUT  | `/profile` |
| PUT  | `/profile/incomes` |
| PUT  | `/profile/assets` |
| GET  | `/profile/workplaces` |
| POST | `/profile/workplaces` |
| PUT  | `/profile/workplaces/{id}` |
| DELETE | `/profile/workplaces/{id}` |
| PUT  | `/profile/preferences` |
| GET  | `/profile/score` | 청약 가점 계산 결과 |

### 2.3 Listings

| Method | Path | 설명 |
|---|---|---|
| GET | `/listings` | 필터: `sido`, `sigungu`, `type`, `priceMin/Max`, `sort` (`match`, `closing`, `price`) |
| GET | `/listings/{id}` | 상세 (units, rules, media 포함) |
| GET | `/listings/{id}/eligibility` | 내 자격 판정 결과 |
| GET | `/listings/{id}/commute` | 내 직장 기준 통근 분석 |
| GET | `/listings/{id}/alternatives` | 근처 실거래 대안 |

### 2.4 Matches (정렬용)

| Method | Path |
|---|---|
| GET | `/matches` | 내 스코어 높은 순 청약 |
| POST | `/matches/recompute` | 프로필 큰 변경 후 수동 재계산 트리거 |

### 2.5 Favorites / Calendar / Notifications

| Method | Path |
|---|---|
| GET | `/favorites` |
| PUT | `/favorites/{listingId}` |
| DELETE | `/favorites/{listingId}` |
| GET | `/calendar?month=2026-05` |
| GET | `/calendar.ics` |
| GET | `/notifications` |
| PUT | `/notifications/settings` |
| POST | `/push-tokens` |

### 2.6 Misc

| Method | Path |
|---|---|
| GET | `/geocode?query=...` | 주소 → 좌표 (내부 캐시) |
| GET | `/actuator/health` |

## 3. 샘플 응답

### GET /listings/{id}

```json
{
  "id": 10234,
  "name": "서초 OO 자이",
  "type": "PRIVATE_SALE",
  "location": { "sido": "서울특별시", "sigungu": "서초구", "address": "...", "lat": 37.49, "lng": 127.02 },
  "applicationStart": "2026-05-10T10:00:00+09:00",
  "applicationEnd":   "2026-05-12T17:00:00+09:00",
  "moveInDate": "2027-12-01",
  "units": [
    { "unitType": "84A", "sizeM2": 84.93, "rooms": 3, "supplyCount": 120, "priceMin": 1500000000, "priceMax": 1620000000 }
  ],
  "supplyTypes": ["FIRST_TIME", "NEWLYWED", "GENERAL"],
  "media": [
    { "kind": "FLOORPLAN", "url": "..." },
    { "kind": "RENDERING", "url": "..." }
  ],
  "rawDocumentUrl": "https://.../announcement.pdf"
}
```

### GET /listings/{id}/eligibility

```json
{
  "eligibleSupplyTypes": ["NEWLYWED", "GENERAL"],
  "bestSupplyType": "NEWLYWED",
  "reasoning": "부부합산 소득이 도시근로자 월평균소득 130% 이내이고 혼인기간 7년 이내 → 신혼부부 특별공급 유리. 생애최초는 소득 기준 초과로 제외.",
  "details": [
    { "supplyType": "FIRST_TIME", "eligible": false, "reasons": ["소득기준 초과 (130% vs 130% 한도)"] },
    { "supplyType": "NEWLYWED", "eligible": true, "expectedCompetition": "MID" },
    { "supplyType": "GENERAL", "eligible": true, "expectedCompetition": "HIGH" }
  ]
}
```

### GET /listings/{id}/commute

```json
{
  "self": {
    "origin": "단지",
    "destination": "강남구 테헤란로 xxx",
    "arriveBy": "09:00",
    "totalMinutes": 52,
    "walkMinutes": 14,
    "transfers": 2,
    "summary": "도보 8분 → 신분당선 → 2호선 강남 → 도보 6분"
  },
  "spouse": { "...": "..." }
}
```
