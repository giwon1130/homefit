# Architecture — homefit

## 1. 시스템 개요

```
┌───────────────┐   ┌──────────────────┐
│ Mobile        │   │ Web              │
│ RN + Expo     │   │ Next.js 15       │
└──────┬────────┘   └──────┬───────────┘
       │ HTTPS / JSON       │
       └─────────┬──────────┘
                 ▼
        ┌────────────────────┐
        │ API (Spring Boot)  │ Railway
        │  - Auth (OAuth+JWT)│
        │  - Profiles        │
        │  - Listings        │
        │  - Matching        │
        │  - Commute         │
        │  - Notifications   │
        └──────┬────────┬────┘
               │        │
               ▼        ▼
     ┌───────────────┐  ┌──────────┐
     │ PostgreSQL 16 │  │ Redis    │ Railway
     │ + PostGIS     │  │ (캐시/큐) │
     └───────┬───────┘  └──────────┘
             ▲
             │
     ┌───────┴────────┐
     │ Ingestion      │ Railway (별도 서비스, cron)
     │  - Crawlers    │
     │  - ETL         │
     │  - Geocoding   │
     └───────┬────────┘
             ▼
     외부: 청약홈, 공공데이터포털, 국토부, LH/SH,
            카카오맵, ODsay, VWorld
```

## 2. 아키텍처 원칙

- **모놀리식 모듈러:** API는 하나의 Spring Boot 앱. 내부는 도메인별 패키지로 분리.
- **ETL 분리:** 크롤링/ETL은 배포 단위가 별개 (실패해도 API 영향 없음).
- **레이어드 + 도메인 중심:**
  - `domain/` — 프레임워크 무관 순수 비즈니스 (엔티티, 값 객체, 도메인 서비스)
  - `application/` — 유스케이스 (트랜잭션 경계)
  - `infrastructure/` — DB, 외부 API, 메시징 어댑터
  - `web/` — REST 컨트롤러, DTO
- **의존성 방향:** `web → application → domain ← infrastructure` (infra는 domain 인터페이스 구현)

## 3. 기술 스택 상세

### 3.1 Backend (`apps/api`)

| 항목 | 선택 | 이유 |
|---|---|---|
| 프레임워크 | Spring Boot 3.3+ | 다른 Playground 레포와 통일 |
| 언어 | Kotlin 1.9+ / Java 21 | 동일 |
| 빌드 | Gradle Kotlin DSL | |
| DB 접근 | Spring JDBC + jOOQ | PostGIS 함수(`ST_DWithin`) 타입세이프 호출 필요 |
| 마이그레이션 | Flyway | |
| 인증 | Spring Security + 커스텀 OAuth2 핸들러 | Google/Kakao 동시 지원 |
| 토큰 | JWT (access 15m + refresh 30d, httpOnly cookie) | |
| 직렬화 | Jackson (kotlin module) | |
| HTTP 클라이언트 | Spring WebClient (reactive) | 외부 API 호출 비동기 |
| 캐시 | Spring Cache + Redis | 통근 경로 결과 30일 TTL |
| 문서화 | springdoc-openapi | `/v3/api-docs` → shared-schema 생성 |
| 관찰성 | Micrometer + Railway metrics | |

### 3.2 Ingestion (`apps/ingestion`)

- Spring Boot (별도 앱, `apps/api`와 도메인 모듈 공유)
- `@Scheduled` + ShedLock (멀티 인스턴스 락)
- 크롤러는 Jsoup + WebClient
- 공고 PDF 파싱: Apache PDFBox + 정규식 룰셋

### 3.3 Web (`apps/web`)

| 항목 | 선택 |
|---|---|
| 프레임워크 | Next.js 15 (App Router) |
| 언어 | TypeScript 5.5+ |
| 서버 상태 | TanStack Query |
| 클라 상태 | Zustand |
| 스타일 | Tailwind CSS + shadcn/ui |
| 폼 | React Hook Form + Zod |
| 지도 | Kakao Map JS SDK |
| 인증 | NextAuth.js (백엔드 JWT 중계) |
| 테스트 | Vitest + Playwright |

### 3.4 Mobile (`apps/mobile`)

| 항목 | 선택 |
|---|---|
| 프레임워크 | React Native 0.81 + Expo 54 |
| 언어 | TypeScript |
| 네비게이션 | Expo Router (file-based) |
| 서버 상태 | TanStack Query |
| 클라 상태 | Zustand |
| 지도 | `@react-native-kakao/map` (또는 WebView 폴백) |
| 푸시 | Expo Notifications → APNs/FCM |
| 인증 | `expo-auth-session` + Kakao SDK |
| 빌드 | Expo prebuild → Xcode → 실기기 |

### 3.5 Shared (`packages/shared-schema`)

- 백엔드 springdoc가 뽑은 OpenAPI 스펙
- `openapi-typescript` → Web/Mobile용 TS 타입
- 관심사항: Enum 동기화, Date/DateTime 포맷 통일 (ISO 8601 KST)

## 4. 주요 플로우

### 4.1 로그인 (Kakao 예)

```
Mobile/Web → Kakao OAuth → code
          → POST /api/v1/auth/kakao { code }
          → Backend exchanges code→token, fetches profile
          → upsert users 테이블
          → JWT access+refresh 발급
          → (웹) httpOnly 쿠키 / (모바일) SecureStore
```

### 4.2 청약 목록 조회

```
GET /api/v1/listings?sort=match_score
  → [캐시 hit] return
  → [miss] 
      1. listings 테이블에서 active 접수 필터
      2. matches 테이블 조인 (user_id 기준 precomputed score)
      3. PostGIS ST_DWithin 으로 거리 1차 필터
      4. 결과 페이징 후 Redis에 TTL 5분 캐시
```

### 4.3 통근 계산 (비동기)

```
User 프로필 업데이트 or 새 listing 유입
  → 이벤트 발행 (Redis Stream)
  → Commute Worker 수신
      1. 캐시 조회 (원점→종점 30일 TTL)
      2. miss면 ODsay 호출
      3. 결과 matches 테이블 upsert
      4. 스코어 재계산
```

## 5. 보안

- 민감 컬럼(소득/자산/주소) → `pgcrypto` 또는 앱 레벨 AES-256 (app-level 추천, 키는 Railway secret)
- API Rate limit: Bucket4j + Redis (익명 10 req/min, 인증 120 req/min)
- CORS: 화이트리스트 (web 도메인만)
- 비밀 관리: Railway Environment Variables
- CSP/HSTS: Next.js 미들웨어
- 의존성 스캔: Dependabot + `./gradlew dependencyCheckAnalyze`

## 6. 관찰성

- **로그:** JSON 포맷 stdout → Railway log → (필요시) Better Stack 전송
- **메트릭:** Micrometer → Railway metrics (p95 응답, 에러율)
- **에러:** Sentry 또는 Rollbar (web/mobile 공통)
- **헬스체크:** `/actuator/health` + Railway healthcheck
