# homefit

청약 · 전세 · 매매 통합 추천 앱. 신혼부부 / 자녀 있는 가족을 타겟으로, **내 조건에 맞는 청약**만 **출퇴근 가능한 지역**에서 **감당되는 금액**으로 골라준다.

## 핵심 차별화
1. **자격 판정 엔진** — 생애최초 / 신혼부부 / 다자녀 중 어느 유형이 유리한지 계산
2. **도어투도어 통근시간** — 본인+배우자 회사 둘 다 고려, 환승/도보 포함한 실제 시간
3. **대안 추천** — 청약 당첨 난이도 높으면 같은 지역의 전/월/매매 자동 제안

## 구성 (모노레포)

```
homefit/
├── apps/
│   ├── api/          # Spring Boot + Kotlin (백엔드 API)
│   ├── ingestion/    # 청약/실거래 크롤러 & ETL
│   ├── web/          # Next.js (웹)
│   └── mobile/       # React Native + Expo (iOS/Android)
├── packages/
│   └── shared-schema/  # OpenAPI → TS/Swift/Kotlin 타입
├── infra/
│   ├── railway/        # Railway 설정
│   └── migrations/     # Flyway SQL
├── docs/               # 설계 문서
└── .github/workflows/  # CI/CD
```

## 문서

| 문서 | 내용 |
|---|---|
| [docs/PRD.md](docs/PRD.md) | 제품 요구사항, 유저 스토리 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 아키텍처 + 기술 스택 |
| [docs/DATA_MODEL.md](docs/DATA_MODEL.md) | DB 스키마 (PostgreSQL + PostGIS) |
| [docs/API_DESIGN.md](docs/API_DESIGN.md) | REST API 설계 |
| [docs/DATA_SOURCES.md](docs/DATA_SOURCES.md) | 외부 API (청약/실거래/지도/경로) |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | GitHub + Railway 배포 플로우 |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | 로컬 세팅 + 코딩 컨벤션 |
| [docs/ROADMAP.md](docs/ROADMAP.md) | 개발 단계 (Phase 0 ~ 4) |

## 빠른 시작

작성 예정 — Phase 0 스캐폴딩 완료 후 업데이트.

## 라이선스

Private / 개인 프로젝트.
