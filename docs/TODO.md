# Backlog

작업 진행 중 미뤄둔 항목들. 우선순위는 위에서 아래로.

## ~~D-1 청약 마감 알림 (이메일)~~ ✅

이메일 발송 + 사용자 토글까지 완료 (PR feat/email-d1). 운영 적용 시 SMTP 환경변수
(`SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `EMAIL_FROM`, `EMAIL_NOTIFICATIONS_ENABLED=true`,
`WEB_BASE_URL`) 만 채우면 즉시 활성화.

## 모바일 푸시 알림 (D-1 / 결과발표)

이메일 다음 단계. 인프라 작업이 큼.

필요 작업:
- `push_tokens` 테이블 (user_id, platform, token, registered_at)
- 토큰 등록 엔드포인트: `POST /api/v1/push-tokens`
- 모바일/웹에서 토큰 발급:
  - iOS: APNs (Expo Notifications)
  - Android: FCM
  - Web: Web Push (VAPID 키 발급)
- 발송 게이트웨이 (이메일 dispatcher 옆에 채널 추가):
  - APNs: Apple Developer 계정 + cert
  - FCM: Firebase 프로젝트
  - Web Push: VAPID + service worker
- 알림 환경설정 확장 (현재 emailEnabled 만 → pushEnabled, 결과발표 토글 등)

## Sentry 에러 트래킹
- DSN 발급 후 api + web 양쪽 SDK 설치
- 401/500 자동 캡처 + 사용자 컨텍스트 (user.id)

## /listings 좌표 정밀화
- LH 누락 단지 (~457개) 수동 큐레이션 admin 페이지
- 또는 lh.or.kr 상세 HTML 파싱 (legal/robots 검토)

## matches 테이블 precompute
- 사용자 수가 많아지면 /match 응답이 느려질 것. 백그라운드 워커가 사용자별 점수 미리 계산해서 DB 저장.
- 현재는 Redis 캐시(30분)로 충분.

## 백업 자동화
- Railway Hobby 자동 백업이 약함. 야간 `pg_dump` → S3 (또는 GitHub release artifact).
