# Development — homefit

## 1. 사전 요구사항

| 도구 | 버전 | 설치 |
|---|---|---|
| JDK | 21 (Temurin) | `brew install --cask temurin21` |
| Kotlin | 1.9+ | Gradle이 관리 |
| Node | 20 LTS | `brew install node@20` |
| pnpm | 9+ | `npm i -g pnpm` |
| Docker Desktop | 최신 | 로컬 Postgres용 |
| Xcode | 16+ | iOS 빌드 |
| Expo CLI | 최신 | `pnpm i -g expo` |
| gh CLI | 최신 | `brew install gh` |
| Railway CLI | 최신 | `brew install railway` |

## 2. 로컬 첫 세팅

```bash
cd /Volumes/Dev/Playground/homefit

# 모노레포 루트 의존성 (pnpm workspace)
pnpm install

# Postgres + PostGIS 로컬
docker compose up -d db redis

# DB 마이그레이션
cd apps/api
./gradlew flywayMigrate

# 각 앱 실행
./gradlew bootRun                  # api (port 8080)
cd ../ingestion && ./gradlew bootRun   # ingestion
cd ../web && pnpm dev              # web (port 3000)
cd ../mobile && pnpm start         # mobile (expo dev server)
```

## 3. 모노레포 구성

루트 `pnpm-workspace.yaml`:
```yaml
packages:
  - "apps/web"
  - "apps/mobile"
  - "packages/*"
```

루트 `package.json` 스크립트:
```json
{
  "scripts": {
    "dev:web": "pnpm -F web dev",
    "dev:mobile": "pnpm -F mobile start",
    "typecheck": "pnpm -r typecheck",
    "lint": "pnpm -r lint"
  }
}
```

Gradle 모듈은 `apps/api/settings.gradle.kts`에서 관리. `ingestion`은 별도 Gradle 루트로 분리하지 않고 settings.gradle에 모듈로 포함 + 별도 main 클래스로 실행.

## 4. 브랜치 / 커밋 컨벤션

- **Trunk-based.** main 하나.
- 브랜치 prefix: `feat/`, `fix/`, `chore/`, `docs/`, `refactor/`
- 커밋 메시지: Conventional Commits
  - `feat(api): add eligibility engine v1`
  - `fix(web): handle empty listings response`
  - `chore(infra): bump flyway`

## 5. 코드 스타일

### Kotlin
- ktlint (Gradle 플러그인)
- 파일당 1 top-level 클래스 원칙
- 패키지: `app.homefit.{domain|application|infrastructure|web}.<area>`
- Nullable 최소화. `Option`/`Result` 대신 sealed class로 명시적 상태 표현

### TypeScript (Web + Mobile)
- ESLint (추천: eslint-config-next + typescript-eslint)
- Prettier (single quote, trailing comma)
- 파일당 1 컴포넌트. 파일명 PascalCase.
- 훅은 `use` prefix, 서버 상태는 TanStack Query hook으로 감싸기

### 공통
- 한 파일 300줄 초과 시 리팩토링 시그널
- 주석은 "왜", 변수/함수명은 "무엇"

## 6. 테스트

### API
- 유닛: JUnit5 + MockK
- 통합: Testcontainers (Postgres + PostGIS 이미지)
- **자격 판정 엔진은 반드시 표로 정리된 케이스 테스트** (소득/자산/가족구성 조합)

### Web
- 유닛: Vitest
- E2E: Playwright (주요 플로우 3~5개만)

### Mobile
- 유닛: Jest + React Native Testing Library

### 공통
- PR마다 CI 통과 필수
- 커버리지 목표: 도메인 레이어 80%, 전체 60%

## 7. 디버깅 팁

### API
- 로컬 DB: `psql postgres://homefit:homefit@localhost:5432/homefit`
- 공간 쿼리 디버깅: `SELECT ST_AsText(geom) FROM listings LIMIT 5;`

### 통근 API 캐시
- Redis: `redis-cli KEYS "commute:*"`

### 모바일
- Expo Dev Menu: 기기 흔들기 / Cmd+D
- 네트워크: Flipper 대신 React Native DevTools (Expo 54 기본 내장)

## 8. 자주 하는 작업

### 스키마 변경
1. `infra/migrations/V{timestamp}__xxx.sql` 추가
2. `apps/api` 도메인 엔티티 업데이트
3. jOOQ 코드 재생성: `./gradlew generateJooq`
4. shared-schema OpenAPI 변경 시 재생성

### 새 엔드포인트
1. OpenAPI 스펙에 먼저 기술하거나, 컨트롤러 생성 후 springdoc이 자동 노출
2. `pnpm -F web generate:api` 로 클라 타입 갱신
3. 모바일/웹 TanStack Query hook 추가

## 9. 포트 / 환경

| 서비스 | 로컬 포트 |
|---|---|
| api | 8080 |
| web | 3000 |
| mobile (Expo dev) | 8081 |
| Postgres | 5432 |
| Redis | 6379 |

`.env.example` 참고해서 `.env.local` 만들기. `.env.local`은 gitignore.
