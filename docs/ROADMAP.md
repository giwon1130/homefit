# Roadmap — homefit

Phase별 "여기까지 되면 다음 단계" 기준. 날짜보다 범위로 관리.

## Phase 0 — 기초 세팅 (스캐폴딩)

**목표:** 빈 앱이 Railway에 배포되어 health check 통과.

- [ ] 모노레포 루트 `pnpm-workspace.yaml`, `package.json`, `.gitignore`, `.editorconfig`
- [ ] `apps/api`: Spring Boot + Kotlin 부트스트랩, `/actuator/health`, Dockerfile
- [ ] `apps/ingestion`: Spring Boot 부트스트랩 (빈 스케줄러)
- [ ] `apps/web`: Next.js 15 초기화
- [ ] `apps/mobile`: Expo 54 초기화 + Expo Router
- [ ] `packages/shared-schema`: 빈 패키지 골격
- [ ] `infra/migrations/V202604240000__enable_extensions.sql`
- [ ] docker-compose.yml (Postgres+PostGIS, Redis)
- [ ] `.github/workflows/ci.yml` (경로 필터 포함)
- [ ] GitHub 리포 생성 + 초기 push
- [ ] Railway 프로젝트 + 서비스 4개 연결 + PostGIS 활성화
- [ ] 배포 후 `https://api.../actuator/health` 200 확인

## Phase 1 — MVP 코어 (웹 중심)

**목표:** 웹에서 로그인 → 프로필 입력 → 맞춤 청약 목록 확인.

- [ ] 인증: Google OAuth + JWT 발급/갱신 (Kakao는 키 발급 후)
- [ ] Profile 도메인: 엔티티, 저장/조회 API, 암호화
- [ ] 청약 가점 자동 계산
- [ ] Ingestion: 공공데이터포털 분양정보 1개 소스 수집 (키 발급 후)
- [ ] Listings 조회 API (필터/정렬, 페이징)
- [ ] 자격 판정 엔진 v1 (신혼부부/생애최초/일반, 룰 버전 관리)
- [ ] Web: 온보딩 위저드 (단계별), 목록/상세, 로그인
- [ ] 기본 알림 1종: D-1 이메일 (푸시는 Phase 2)

**나오는 산출물 예시:**
- 유저가 Google 로그인 후 프로필 10분 입력
- "내 조건에 맞는 청약 N건" 목록 표시
- 각 카드에 유형별 자격 뱃지 + 예상 경쟁률 밴드

## Phase 2 — 차별화 (통근 + 매칭)

**목표:** "통근 가능성" 킬러 기능 완성 + 모바일 앱 기본 화면.

- [ ] Kakao 키 발급 후 지도/지오코딩 연동
- [ ] ODsay 통근 계산 워커 + commute_cache
- [ ] PostGIS 기반 지역 필터 (`ST_DWithin`)
- [ ] 매칭 스코어 계산 (자격/통근/예산/선호 가중합)
- [ ] 매칭 precompute 배치 (프로필 변경 시 enqueue)
- [ ] 모바일 기본 화면: 온보딩, 홈(추천 목록), 상세
- [ ] 푸시 알림 (APNs via Expo)
- [ ] 즐겨찾기

## Phase 3 — 확장 (대안 추천 + 지도)

**목표:** 청약 부적합자에게도 유용한 앱.

- [ ] 국토부 실거래가 수집 + 파티셔닝
- [ ] 대안 매물 추천 카드 (상세에서 "근처 전세/매매")
- [ ] 행복주택 / 매입임대 / 전세임대 소스 추가
- [ ] 캘린더 뷰 + .ics 내보내기
- [ ] 지도 화면: 단지 핀 + 내 직장 반경 시각화
- [ ] 웹/모바일 다크모드

## Phase 4 — 완성도

**목표:** 실제 사용자에게 공유 가능한 품질.

- [ ] 단지 이미지/조감도 큐레이션 (공고 PDF 파싱 or 수동)
- [ ] 접근성 패스 (VoiceOver/TalkBack, 다이나믹 타입)
- [ ] 성능 튜닝 (p95 < 500ms 확인)
- [ ] 에러 트래킹 (Sentry)
- [ ] TestFlight 배포
- [ ] 법규 변경 대응 프로세스 문서화
- [ ] (선택) 안드로이드 빌드
- [ ] (선택) 모기지 한도 계산기

## Phase 5+ — 아이디어 풀

- 당첨 이후 입주 전 체크리스트
- 단지 커뮤니티 리뷰 (신중)
- 청약가점 시뮬레이터 (무주택기간 +1년 시나리오)
- 부동산 중개사 연결 (수익화)
- AI 추천 근거 자연어 생성

## 의존성 / 블로커

| 블로커 | 영향 Phase | 완화 |
|---|---|---|
| 공공데이터포털 API 키 승인 | Phase 1 | 승인 전까지는 샘플 JSON 파일 fixture로 개발 |
| Kakao 개발자 앱 등록 | Phase 2 | 지도/OAuth 둘 다 막힘 → 우선 Google OAuth만으로 Phase 1 완수 |
| ODsay 키 | Phase 2 | 대안: 카카오모빌리티. 둘 다 없으면 직선거리 기반 임시 스코어 |
