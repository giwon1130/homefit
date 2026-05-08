# TestFlight 첫 배포 런북

Apple Developer 멤버십 활성화 + Sign in with Apple (PR #68) + EAS Build
셋업 (PR #69) + 플레이스홀더 아이콘 (이 PR) 완료된 상태에서 진행.

## 0. 사전 준비 체크리스트

| 항목 | 확인 |
|---|---|
| Apple Developer 멤버십 | ✅ 활성 |
| `apps/mobile/app.json` ios.bundleIdentifier | `app.homefit.mobile` |
| `apps/mobile/app.json` ios.usesAppleSignIn | `true` |
| `apps/mobile/eas.json` 프로필 | development / preview / production 3개 |
| 아이콘 (`./assets/icon.png`, 1024×1024) | placeholder 있음 (디자인 확정 후 교체) |
| Apple Push key (APNs) | EAS 가 첫 빌드 때 자동 발급 |
| App Store Connect 앱 등록 | ❌ **미등록** — 아래 1번 수동 |

## 1. App Store Connect 앱 등록 (1회, 웹 UI)

1. https://appstoreconnect.apple.com → 내 앱 → ➕
2. **플랫폼**: iOS
3. **이름**: homefit
4. **기본 언어**: 한국어 (Korean)
5. **번들 ID**: `app.homefit.mobile` (없으면 https://developer.apple.com/account/resources/identifiers/list 에서 먼저 등록 — Sign In with Apple capability 체크)
6. **SKU**: `homefit-001` (임의)
7. **사용자 권한**: 본인

저장 후 ASC App ID (숫자) 메모 → `apps/mobile/eas.json` 의 `submit.production.ios.ascAppId` 갱신 (별도 작은 PR 가능).

## 2. EAS 프로젝트 초기화 (로컬, 1회)

```bash
# Expo 계정 로그인
pnpm dlx eas-cli@latest login

# apps/mobile 안에서 초기화 — projectId 자동 발급 + app.json 갱신
cd apps/mobile
pnpm dlx eas-cli@latest init
```

`eas init` 결과로:
- `extra.eas.projectId` 자동 갱신됨
- `updates.url` 의 `PROJECT_ID_REPLACE` 도 같이 교체 필요 (수동)

→ git diff 확인 후 작은 PR (`chore(mobile): wire EAS projectId`) 로 커밋.

## 3. iOS Credentials (자동, 첫 빌드에서)

```bash
cd apps/mobile
pnpm dlx eas-cli@latest credentials -p ios
```

대화형 메뉴:
- "Use the default credentials EAS provides" 선택
- iOS Distribution Certificate, Provisioning Profile 자동 생성
- Push Notifications Key 자동 생성 (Expo Push 용)
- Sign in with Apple capability 자동 활성화 (`usesAppleSignIn=true` 가 app.json 에 있어서)

## 4. 첫 production 빌드

```bash
cd apps/mobile
pnpm dlx eas-cli@latest build -p ios --profile production
```

- 시간: 보통 10~20분 (m-medium 큐).
- 끝나면 EAS 가 빌드 결과 URL 출력 + ASC 자동 업로드 옵션 안내.

## 5. TestFlight 업로드

```bash
pnpm dlx eas-cli@latest submit -p ios --latest
```

업로드 완료 후 ASC → TestFlight → "내부 테스트" 그룹 생성 → 본인 Apple ID 추가 →
TestFlight 앱(아이폰)에 초대 메일 도착.

## 6. TestFlight 메타데이터 (제출 전)

App Store Connect → TestFlight → 빌드 → 테스트 정보:
- **연락처 이메일**: 본인
- **테스트 노트**: 무엇을 시험해달라
- **베타 앱 설명**: "신혼부부/가족 청약 추천 앱"

내부 테스트는 심사 없이 즉시 가능. 외부 테스트는 첫 1회 베타 심사 (보통 24h 내).

## 7. App Store 정식 제출 (선택, 추후)

심사 받으려면 추가:
- 앱 아이콘 디자인 확정 (`./assets/icon.png` 교체)
- 스크린샷 6.7" / 6.1" (필수 사이즈)
- 개인정보 처리방침 URL
- 앱 분류 / 키워드 / 설명
- Sign in with Apple 활성 (이미 됨 ✅)

## 알려진 함정

1. **iOS 빌드 첫 실패 — bundle ID 못 찾음**: Apple Developer Identifiers 에 등록 안 됐을 때.
   해결: https://developer.apple.com/account → Identifiers → ➕ → App IDs → bundle id `app.homefit.mobile` + Sign In with Apple capability.
2. **Push 토큰 발급 실패 — APNs key 누락**: EAS credentials 메뉴에서 "Set up push notifications" 선택.
3. **Sign In with Apple 첫 진입 후 fullName 안 옴**: Apple 정책상 첫 로그인 1회만 제공. 재로그인은 token 만 옴 — 이미 backend 가 sub 로 매칭하고 fallback email 만들어서 처리 (#68 의 `signInWithApple`).
4. **Updates EAS_TOO_OLD**: `runtimeVersion.policy=appVersion` 이라 1.0.0 → 1.1.0 으로 올리면 OTA 안 가고 새 binary 필요.
