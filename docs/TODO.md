# Backlog

작업 진행 중 미뤄둔 항목들. 우선순위는 위에서 아래로.

## ~~D-1 청약 마감 알림 (이메일)~~ ✅

이메일 발송 + 사용자 토글까지 완료 (PR feat/email-d1). 운영 적용 시 SMTP 환경변수
(`SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `EMAIL_FROM`, `EMAIL_NOTIFICATIONS_ENABLED=true`,
`WEB_BASE_URL`) 만 채우면 즉시 활성화.

## ~~모바일 푸시 알림 (D-1)~~ ✅

iOS/Android 푸시 알림 완료 — Expo Push Service 사용 (APNs/FCM 추상화).
웹 푸시(VAPID) 와 결과발표 알림은 다음 단계.

남은 후속:
- 결과발표 알림 (announcementDate 윈도우)
- 웹 푸시 (VAPID 키 발급 + service worker)
- iOS Production APNs 인증 (Expo 가 sandbox/production 자동 분기 — TestFlight 업로드 시 production 자동)

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
