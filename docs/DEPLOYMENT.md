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

## 5. iOS 빌드 (EAS Build → TestFlight)

Apple Developer 멤버십 활성화 후 EAS Build 사용. `apps/mobile/eas.json` 에
3개 프로필 (development / preview / production) 정의됨.

> 첫 TestFlight 배포 단계별 런북: [`docs/TESTFLIGHT.md`](./TESTFLIGHT.md)

### 5.1 첫 셋업 (1회)

```bash
# Expo 계정 로그인 + EAS CLI 설치
pnpm dlx eas-cli@latest login
pnpm dlx eas-cli@latest init   # apps/mobile 안에서, projectId 자동 발급

# eas init 결과 → apps/mobile/app.json 의 extra.eas.projectId 갱신 (PR 로 커밋)
# updates.url 의 PROJECT_ID_REPLACE 도 실제 ID 로 교체
```

Apple credentials 는 첫 빌드 시 EAS 가 자동 발급/관리:
- iOS Distribution Certificate
- Provisioning Profile
- Push Notifications Key (Expo Push 용)
- Sign in with Apple capability (`app.json` 의 `usesAppleSignIn=true` 자동 인식)

### 5.2 일반 빌드 사이클

```bash
cd apps/mobile

# 시뮬레이터 빌드 (개발 중 빠른 확인)
pnpm dlx eas-cli build -p ios --profile development

# 내부 배포 (TestFlight 전 단계, ad-hoc 실기기)
pnpm dlx eas-cli build -p ios --profile preview

# TestFlight / App Store 제출용
pnpm dlx eas-cli build -p ios --profile production
pnpm dlx eas-cli submit -p ios --latest    # → App Store Connect 업로드
```

`production` 프로필은 `autoIncrement: true` 라 buildNumber 자동 증가.

### 5.3 OTA 업데이트 (네이티브 변경 없을 때)

```bash
pnpm dlx eas-cli update --branch production --message "fix: ..."
```

`runtimeVersion.policy=appVersion` 이라 같은 1.0.x 안에선 OTA 가능, 1.1.0
이상으로 올라가면 새 binary 빌드 필요.

### 5.4 환경별 분기 (선택)

운영 vs 스테이징 API 를 갈라 쓰려면:
- `eas.json` 의 build.profile 마다 `env.EXPO_PUBLIC_API_BASE_URL` 지정
- 현재는 `apps/mobile/app.json` 의 `extra.apiBaseUrl` 단일 값 사용

### 5.5 옛 흐름 (참고용)

EAS 셋업 전엔 로컬 prebuild + Xcode 로 띄웠음:
```bash
cd apps/mobile
pnpm expo prebuild --platform ios
open ios/homefit.xcworkspace
```

## 6. 비용 예상 (Railway)

- Hobby Plan $5/월: 프로젝트 1개, 리소스 제한 내 사용
- Postgres + Redis + 2 services: 트래픽 적으면 $5~10/월 수준
- Vercel Hobby: 무료
- 카카오/공공데이터: 무료 티어 내 사용 가능

## 7. 롤백 / 장애 대응

### 7.1 배포 롤백

- Railway: Deployments 탭 → 이전 배포 Redeploy
- 긴급 읽기전용 모드: feature flag 로 쓰기 엔드포인트 차단

### 7.2 DB 백업

`.github/workflows/backup-db.yml` 이 매일 KST 03:00 (UTC 18:00) 에
Railway Postgres 를 `pg_dump --format=custom` 으로 받아서 GitHub release
의 attached asset 으로 업로드. 30일 이상 된 백업은 자동 prune.

**필수 secret**: GitHub repo → Settings → Secrets → Actions
- `BACKUP_DATABASE_URL` — Railway Postgres 의 read-only / 또는 연결 가능한
  full URL (`postgresql://user:pass@host:port/db`). 보통 Railway 가 제공하는
  `DATABASE_URL` 을 그대로 쓰면 됨.

**수동 실행**: GitHub Actions UI → DB Backup → Run workflow
또는 로컬:
```bash
DATABASE_URL=postgres://... ./scripts/backup-db.sh
```

### 7.3 DB 복구

GitHub release `db-backup-YYYY-MM-DD` 의 `.dump` 파일 다운로드 후:

```bash
# 새 빈 DB 에 복구 (운영 DB 덮어쓰지 말 것 — 먼저 staging 으로 검증)
pg_restore --no-owner --no-acl --clean --if-exists \
  -d "postgresql://user:pass@host:port/restore_target" \
  homefit-XXXX.dump
```

PostGIS 확장은 Flyway 가 첫 마이그레이션에서 자동 활성화하므로
`CREATE EXTENSION` 을 미리 할 필요는 없음.
