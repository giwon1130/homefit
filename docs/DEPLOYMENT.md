# Deployment — homefit

## 1. 전체 플로우

```
로컬 작업 → GitHub push (main)
          ├─→ Railway API 서비스     (Dockerfile, 자동 빌드/배포)
          ├─→ Railway Ingestion 서비스 (Dockerfile)
          └─→ Vercel Web (선택)       혹은 Railway Web
```

iOS 앱은 Expo prebuild → Xcode → 실기기. 추후 EAS Build + TestFlight.

## 2. GitHub 저장소 설정

### 2.1 리포 생성 (1회)

```bash
cd /Volumes/Dev/Playground/homefit
git init
git branch -M main
# GitHub에서 빈 리포 'homefit' 생성 (Private 권장)
git remote add origin git@github.com:<username>/homefit.git
git add .
git commit -m "chore: initial docs and repo structure"
git push -u origin main
```

### 2.2 브랜치 보호 (GitHub Settings)
- main 브랜치: PR 없이 직접 푸시 허용 (1인 개발). 추후 필요시 조임.
- Force push 금지 (main)
- Secret scanning, Dependabot 활성화

### 2.3 CI (GitHub Actions)

`.github/workflows/ci.yml`에 다음 잡:
- `api`: `./gradlew check`
- `web`: `pnpm -F web lint && pnpm -F web typecheck`
- `mobile`: `pnpm -F mobile typecheck`
- 경로 필터로 변경된 앱만 실행

## 3. Railway 설정

### 3.1 프로젝트 구조

**1 Project = homefit (prod only, 사용자 결정)**

서비스 4개:
1. **api** — Spring Boot 서버 (`apps/api/Dockerfile`)
2. **ingestion** — ETL 워커 (`apps/ingestion/Dockerfile`, cron)
3. **postgres** — Railway Postgres 플러그인 + PostGIS 확장
4. **redis** — Railway Redis 플러그인

웹은 **Vercel 권장** (Next.js 최적화 좋음). Railway에 같이 두고 싶으면 서비스 5번으로 추가.

### 3.2 Railway 연결 (서비스별 1회)

Railway 대시보드에서 각 서비스별로:
1. **New Service → Deploy from GitHub repo** → `homefit` 선택
2. **Root directory** 지정:
   - api: `apps/api`
   - ingestion: `apps/ingestion`
3. **Build command:** Docker 자동 감지 (Dockerfile 존재 시)
4. **Watch paths** 설정 (모노레포라서 변경된 앱만 재배포):
   - api: `apps/api/**`, `packages/shared-schema/**`, `infra/migrations/**`
   - ingestion: `apps/ingestion/**`, `packages/shared-schema/**`

### 3.3 환경변수

Railway 대시보드 → 각 서비스 → Variables:

**api / ingestion 공통:**
```
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=${{Postgres.DATABASE_URL}}
REDIS_URL=${{Redis.REDIS_URL}}
JWT_SECRET=<랜덤 64바이트>
AES_ENCRYPTION_KEY=<랜덤 32바이트>
GOOGLE_OAUTH_CLIENT_ID=
GOOGLE_OAUTH_CLIENT_SECRET=
KAKAO_OAUTH_CLIENT_ID=
KAKAO_OAUTH_CLIENT_SECRET=
PUBLIC_DATA_API_KEY=
MOLIT_API_KEY=
KAKAO_REST_API_KEY=
ODSAY_API_KEY=
```

**web (Vercel):**
```
NEXT_PUBLIC_API_BASE_URL=https://api.homefit.app
NEXT_PUBLIC_KAKAO_JS_KEY=
NEXTAUTH_URL=https://homefit.app
NEXTAUTH_SECRET=
```

### 3.4 자동 배포

main 브랜치 push 시 Railway가 감지 → `Dockerfile` 빌드 → 배포. 무중단 교체 (기본).

### 3.5 PostGIS 활성화 (1회)

Railway Postgres 접속 후:
```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

또는 Flyway 첫 마이그레이션으로 처리 (권장).

### 3.6 헬스체크 / 무중단

- Spring Boot: `/actuator/health` 노출
- Railway 서비스 설정: Health check path 지정, 실패 시 rollback

## 4. 도메인 (선택)

- Railway: `api-homefit.up.railway.app` 기본, 커스텀 도메인 `api.homefit.app` 연결 가능
- Vercel: `homefit.app` → Vercel DNS
- 구매: Cloudflare Registrar 권장 (원가)

## 5. iOS 빌드 (당장은 로컬)

Phase 0~2: Expo prebuild → Xcode 로컬 서명 → 실기기
Phase 3+: EAS Build → TestFlight

```bash
cd apps/mobile
pnpm expo prebuild --platform ios
open ios/homefit.xcworkspace
# Xcode: Signing & Capabilities → Team 설정 → Run
```

## 6. 비용 예상 (Railway)

- Hobby Plan $5/월: 프로젝트 1개, 리소스 제한 내 사용
- Postgres + Redis + 2 services: 트래픽 적으면 $5~10/월 수준
- Vercel Hobby: 무료
- 카카오/공공데이터: 무료 티어 내 사용 가능

## 7. 롤백 / 장애 대응

- Railway: Deployments 탭 → 이전 배포 Redeploy
- DB: Railway 자동 백업 (Pro 플랜), Hobby는 주기적 `pg_dump`를 GitHub Actions로 돌려 Private 리포에 저장
- 긴급 읽기전용 모드: feature flag로 쓰기 엔드포인트 차단
