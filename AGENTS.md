# homefit — Agent 작업 가이드

이 문서는 이 리포에서 작업하는 에이전트(Claude 등)가 따라야 할 지침이다. `/Volumes/Dev/Playground/AGENT.md` 의 공통 규칙을 우선 따르고, 여기에는 homefit 고유 내용만 적는다.

## 프로젝트 요약

청약/전세/매매 통합 추천 앱. 신혼부부/가족 타겟. 통근시간과 자격 판정이 차별화 포인트. 자세한 내용은 [README.md](README.md) · `docs/` · [docs/ROADMAP.md](docs/ROADMAP.md) 참조.

## 기술 스택 (요약)

- **Backend:** Spring Boot 3.3 · Kotlin · Java 21 · Spring JDBC · Flyway · jjwt (v0.12)
- **Web:** Next.js 15 (App Router) · NextAuth v5 beta · TypeScript · Tailwind CSS · TanStack Query
- **Mobile:** React Native 0.81 · Expo 54 · TypeScript (스캐폴딩 단계)
- **DB:** PostgreSQL 16 · pgcrypto (PostGIS는 Phase 2에서 도입)
- **Auth:** JWT · Google OAuth (Kakao는 Phase 2+)
- **Deploy:** Railway (main push → 자동 Docker 빌드)

## 라이브 엔드포인트

- API — https://api-production-1d45.up.railway.app
- Ingestion — https://ingestion-production-fd56.up.railway.app
- Web — https://web-production-ac95e.up.railway.app

Railway 프로젝트 ID: `6cd55bf0-ed63-4e79-ab8d-c99449de9824`. 서비스 5개(api, ingestion, web, Postgres, Redis).

## 코딩 원칙

1. **한 파일에 다 넣지 말 것.** 300줄 넘어가면 분리 검토. 클래스/모듈 하나 = 역할 하나.
2. **레이어 의존성 방향 지키기.** `web → application → domain ← infrastructure`. 도메인은 프레임워크 몰라야 함.
3. **민감정보는 절대 커밋 금지.** 소득·자산은 `AesGcmEncryptor` 로 DB 필드 레벨 암호화 (`incomes.*_enc`, `assets.*_enc`).
4. **외부 API 호출은 인프라 레이어에서만.** 도메인 코드가 WebClient/OkHttp 직접 알면 안 됨.
5. **주석은 "왜"에만.** "무엇"은 코드가 설명.
6. **자격 판정 / 가점 계산 / 암호 / JWT는 단위 테스트 필수.** 법규/보안 변경 시 회귀 방어.
7. **Controller는 얇게.** DTO ↔ 도메인 매핑만, 비즈니스는 Service 로.

## 모노레포 작업 규칙

- **경로 고정:** 항상 `/Volumes/Dev/Playground/homefit` 에서 작업. SSD 언마운트면 먼저 알리고 멈춤.
- **패키지 매니저:** pnpm 9 (corepack 통해 활성화). `pnpm -F <name>` 으로 범위 한정.
- **변경 범위 명확히:** `apps/api` 만 고치면 `apps/web` 은 건드리지 말 것.
- **DB 마이그레이션:** `apps/api/src/main/resources/db/migration/` 에만 두기 (ingestion은 마이그레이션 소유 안 함).
- **공유 enum/상수:** `packages/shared-schema` 사용. 단 현재는 TS 전용 (Kotlin은 각 앱이 자체 enum 정의, 같은 문자열 코드로 호환).

## 검증 루틴 (커밋 전)

| 앱 | 명령 |
|---|---|
| `apps/api` | `cd apps/api && ./gradlew test` (contextLoads 포함) |
| `apps/ingestion` | `cd apps/ingestion && ./gradlew compileKotlin && ./gradlew test` |
| `apps/web` | `pnpm -F @homefit/web typecheck && pnpm -F @homefit/web build` |
| `apps/mobile` | `pnpm -F @homefit/mobile typecheck` |

## Git 플로우

1. 작업 시작 전 `git pull origin main`.
2. feature 브랜치에서 작업 (`feat/`, `fix/`, `chore/`, `docs/`, `refactor/` prefix).
3. PR 생성 → 셀프 리뷰 → **squash merge** → 브랜치 삭제.
4. main 머지 시 Railway 자동 배포 (2~3분).
5. main 직접 push 금지 (feature 브랜치 + PR만).

## Railway / 배포 주의사항

- **서비스 생성은 대시보드에서만** 가능해요. `railway add --service --repo` 는 CLI 버그로 GitHub 연동 시 Unauthorized 떨어짐. 서비스 만든 뒤에는 env 주입, 도메인 생성, 배포 모니터링 전부 CLI로 가능.
- **Root Directory 설정 필수** — `apps/api`, `apps/ingestion`, `apps/web` 각각 서비스 Settings에서 지정해야 올바른 Dockerfile 로딩.
- **Railway 기본 Postgres는 PostGIS 미포함.** Phase 2에서 `postgis/postgis` 이미지 기반 서비스로 교체 예정.
- **Next.js CVE 가드.** Railway는 알려진 취약점이 있는 npm 의존성을 거부함 (예: next@15.0.3 블록). 업그레이드 지시에 맞춰 바로 bump.

## 시크릿 취급

- Railway 환경변수가 단일 진실 저장소.
- 임시 파일(`/tmp/homefit_*`) 은 주입 후 **즉시 삭제**.
- `railway variables --service X --kv` 출력은 값까지 포함됨 → **이름만 보려면 `awk -F= '{print $1}'`** 필터링.
- 로그/채팅에 시크릿 흘리면 즉시 회전 (NEXTAUTH_SECRET 등 내가 생성한 건 `secrets.token_urlsafe(48)` 로 재생성, OAuth secret 등 외부 발급은 콘솔에서 재발급 요청).

## 절대 금지

- `git push --force` to main
- 개인정보가 들어간 테이블을 seed 데이터로 덤프
- 청약홈/LH 사이트를 robots.txt 위반하며 긁기 (공공데이터 API 우선)
- 프로덕션 DB 직접 쿼리 (사용자가 명시 허가하지 않는 한)

## 주요 엔드포인트 맵

공개:
- `GET /actuator/health` · `GET /api/v1/ping`
- `GET /api/v1/listings` · `GET /api/v1/listings/{id}`

인증 필요 (Bearer JWT):
- `GET/PUT /api/v1/profile` (하위 리소스 포함)
- `GET /api/v1/profile/score`
- `GET /api/v1/listings/{id}/eligibility`
- `GET /api/v1/auth/me`

Auth:
- `POST /api/v1/auth/google` { idToken } → 토큰 쌍 발급
- `POST /api/v1/auth/refresh` { refreshToken } → 회전

Admin (ingestion):
- `POST /admin/ingestion/run` (헤더 `X-Admin-Token`)
