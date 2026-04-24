# homefit

청약 · 전세 · 매매 통합 추천 앱. 신혼부부 / 자녀 있는 가족을 타겟으로, **내 조건에 맞는 청약**만 **출퇴근 가능한 지역**에서 **감당되는 금액**으로 골라준다.

## 현재 상태 (Phase 1)

| 영역 | 상태 |
|---|---|
| 청약 데이터 수집 | ✅ 청약홈 분양정보 API 연동, 6시간 cron 싱크 |
| Google OAuth + JWT | ✅ 웹 로그인 흐름 |
| 프로필 + 청약 가점 | ✅ AES-256 민감필드 암호화 |
| 자격 판정 엔진 v1 | ✅ 생애최초/신혼부부/다자녀/일반 |
| 청약 목록/상세 웹 | ✅ Next.js 15 + Tailwind |
| 통근 시간 계산 | 🔜 Phase 2 (PostGIS + ODsay) |
| 모바일 앱 | 🔜 Phase 2+ (Expo 스캐폴딩만) |

## 라이브 환경 (Railway)

- **API:** https://api-production-1d45.up.railway.app  (`/actuator/health`, `/api/v1/*`)
- **Ingestion:** https://ingestion-production-fd56.up.railway.app (`/admin/ingestion/run` 토큰 보호)
- **Web:** https://web-production-ac95e.up.railway.app

main 브랜치 push → Railway 자동 재배포. Flyway 마이그레이션도 API 배포에 포함.

## 핵심 차별화

1. **자격 판정 엔진** — 생애최초 / 신혼부부 / 다자녀 중 어느 유형이 유리한지 계산
2. **도어투도어 통근시간** (Phase 2) — 본인+배우자 회사 둘 다 고려, 환승/도보 포함 실제 시간
3. **대안 추천** (Phase 3) — 청약 당첨 난이도 높으면 같은 지역의 전/월/매매 자동 제안

## 구성 (모노레포)

```
homefit/
├── apps/
│   ├── api/          # Spring Boot 3.3 + Kotlin + Java 21
│   ├── ingestion/    # 크롤러/ETL (동일 스택, 스케줄러)
│   ├── web/          # Next.js 15 + NextAuth + Tailwind
│   └── mobile/       # React Native + Expo (스캐폴딩)
├── packages/
│   └── shared-schema/  # 공유 타입/상수
├── docs/               # 설계 문서 (최신 아키텍처/데이터 모델/로드맵)
├── .github/workflows/  # CI (경로 필터)
└── .claude/launch.json # 로컬 dev 서버 구성
```

## 기술 스택

| 레이어 | 선택 |
|---|---|
| 백엔드 | Spring Boot 3.3 · Kotlin 1.9 · Java 21 · Spring JDBC · Flyway |
| 인증 | JWT (HS256) · Google OAuth id_token · 회전식 refresh token |
| 암호화 | AES-256-GCM (필드 레벨 민감정보) |
| DB | PostgreSQL 16 · pgcrypto (PostGIS는 Phase 2 도입) |
| 캐시/큐 | Redis 7 |
| 웹 | Next.js 15 App Router · NextAuth v5 beta · Tailwind · TanStack Query |
| 모바일 | React Native 0.81 · Expo 54 |
| 배포 | Railway (Dockerfile 빌드, 서비스 5개) |

## 외부 API

- **공공데이터포털** — 한국부동산원 청약홈 분양정보 (`ApplyhomeInfoDetailSvc`)
- **Google OAuth** — 로그인
- Phase 2+ 예정: LH 공고, 국토부 실거래가, 카카오맵/모빌리티, ODsay

## 문서

| 문서 | 내용 |
|---|---|
| [docs/PRD.md](docs/PRD.md) | 제품 요구사항, 유저 스토리 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 아키텍처 + 기술 스택 |
| [docs/DATA_MODEL.md](docs/DATA_MODEL.md) | DB 스키마 |
| [docs/API_DESIGN.md](docs/API_DESIGN.md) | REST API 설계 |
| [docs/DATA_SOURCES.md](docs/DATA_SOURCES.md) | 외부 API |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | GitHub + Railway 배포 |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | 로컬 세팅 + 코딩 컨벤션 |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Phase 0 ~ 5 |

## 빠른 시작 (로컬)

```bash
# 1. 인프라 (Postgres + Redis)
docker compose up -d db redis

# 2. 백엔드 (마이그레이션 자동 실행)
cd apps/api && ./gradlew bootRun   # port 8080

# 3. 웹
pnpm install
pnpm -F @homefit/web dev           # port 3000

# 4. (선택) Ingestion — API 키 필요
cd apps/ingestion && ./gradlew bootRun  # port 8081
```

필요한 환경변수는 `.env.example` 참조. 구글 OAuth 클라이언트는 `http://localhost:3000/api/auth/callback/google` 을 리디렉션에 등록해야 함.

## 라이선스

Private / 개인 프로젝트.
