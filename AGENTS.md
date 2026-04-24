# homefit — Agent 작업 가이드

이 문서는 이 리포에서 작업하는 에이전트(Claude 등)가 따라야 할 지침이다. `/Volumes/Dev/Playground/AGENT.md`의 공통 규칙을 우선 따르고, 여기에는 homefit 고유 내용만 적는다.

## 프로젝트 요약

청약/전세/매매 통합 추천 앱. 신혼부부/가족 타겟. 통근시간과 자격 판정이 차별화 포인트. 자세한 내용은 [README.md](README.md)와 `docs/` 참조.

## 기술 스택 (요약)

- **Backend:** Spring Boot 3.x, Kotlin, Java 21, Spring JDBC + Flyway, jOOQ(PostGIS 쿼리용)
- **Web:** Next.js 15 (App Router), TypeScript, TanStack Query, Tailwind + shadcn/ui
- **Mobile:** React Native 0.81.x + Expo 54, TypeScript
- **DB:** PostgreSQL 16 + PostGIS
- **Auth:** JWT + Google OAuth + Kakao OAuth
- **Deploy:** Railway (main 브랜치 push → 자동 배포)

## 코딩 원칙

1. **한 파일에 다 넣지 말 것.** 300줄 넘어가면 분리 검토. 클래스 하나 = 역할 하나.
2. **레이어 의존성 방향 지키기.** 도메인 → 애플리케이션 → 인프라/웹. 역방향 금지.
3. **민감정보는 절대 커밋 금지.** 소득/자산/주소는 DB 필드 레벨에서 암호화.
4. **외부 API 호출은 인프라 레이어에서만.** 도메인 코드가 HTTP 클라이언트를 직접 알면 안 됨.
5. **주석은 "왜"에만.** "무엇"은 코드가 설명하므로 주석 달지 말 것.
6. **TDD 강제는 아니지만, 자격 판정 엔진은 반드시 단위 테스트.** 법규 변경 시 회귀 잡아야 함.

## 모노레포 작업 규칙

- **경로 고정:** 항상 `/Volumes/Dev/Playground/homefit`에서 작업. SSD 언마운트면 먼저 알리고 멈춤.
- **변경 범위 명확히:** `apps/api`만 고치면 `apps/web`은 건드리지 말 것.
- **스키마 변경은 반드시 shared-schema 업데이트 동반.** OpenAPI 먼저 → 백엔드 → 클라이언트 순.

## 검증 루틴 (커밋 전)

| 앱 | 명령 |
|---|---|
| `apps/api` | `./gradlew compileKotlin` 후 `./gradlew test` |
| `apps/web` | `pnpm -F web typecheck && pnpm -F web lint` |
| `apps/mobile` | `pnpm -F mobile typecheck` |

## Git 플로우

1. 작업 시작 전 `git pull origin main`
2. feature 브랜치에서 작업 (`feat/`, `fix/`, `chore/` prefix)
3. PR 생성 → 셀프 리뷰 → merge
4. main 머지 시 Railway 자동 배포 (docker build)

## 절대 금지

- `git push --force` to main
- 개인정보가 들어간 테이블을 seed 데이터로 덤프
- 청약홈/LH 사이트를 공식 robots.txt 위반하며 긁기
