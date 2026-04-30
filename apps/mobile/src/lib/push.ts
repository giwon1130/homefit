import * as Notifications from "expo-notifications";
import * as Device from "expo-device";
import Constants from "expo-constants";
import { Platform } from "react-native";
import * as SecureStore from "expo-secure-store";
import { apiFetch } from "./api";

const TOKEN_KEY = "homefit.pushToken";

/**
 * 권한 요청 + Expo Push Token 발급 + 백엔드 등록.
 * 시뮬레이터에서는 Device.isDevice = false → null 반환 (실기기 전용).
 *
 * 호출 위치:
 *  - 로그인 직후 (loginScreen 의 useGoogleSignIn onSuccess)
 *  - 앱 부팅 시 토큰 갱신 (옵션)
 */
export async function registerPushToken(): Promise<string | null> {
  if (!Device.isDevice) return null;

  // 권한
  const settings = await Notifications.getPermissionsAsync();
  let granted = settings.status === "granted";
  if (!granted) {
    const req = await Notifications.requestPermissionsAsync();
    granted = req.status === "granted";
  }
  if (!granted) return null;

  // Android 채널 설정 (없으면 알림 무음/안 뜸).
  if (Platform.OS === "android") {
    await Notifications.setNotificationChannelAsync("default", {
      name: "default",
      importance: Notifications.AndroidImportance.HIGH,
      lightColor: "#2563eb",
    });
  }

  // EAS projectId 가 있어야 ExponentPushToken 발급 가능.
  const projectId =
    (Constants.expoConfig?.extra as { eas?: { projectId?: string } } | undefined)?.eas?.projectId
    ?? (Constants as { easConfig?: { projectId?: string } }).easConfig?.projectId;

  let tokenValue: string;
  try {
    const t = await Notifications.getExpoPushTokenAsync(
      projectId ? { projectId } : undefined,
    );
    tokenValue = t.data;
  } catch (e) {
    console.warn("getExpoPushTokenAsync failed", e);
    return null;
  }

  // 백엔드 등록 (idempotent — token unique key).
  try {
    const res = await apiFetch("/api/v1/push-tokens", {
      method: "POST",
      body: JSON.stringify({ token: tokenValue, platform: "EXPO" }),
    });
    if (res.ok) {
      await SecureStore.setItemAsync(TOKEN_KEY, tokenValue);
      return tokenValue;
    }
    console.warn("push-token register HTTP", res.status);
    return null;
  } catch (e) {
    console.warn("push-token register failed", e);
    return null;
  }
}

/**
 * 로그아웃 시 호출. 백엔드에서 토큰 삭제 + secure store 정리.
 * 실패해도 무시 (오프라인 로그아웃 등).
 */
export async function unregisterPushToken(): Promise<void> {
  const token = await SecureStore.getItemAsync(TOKEN_KEY);
  if (!token) return;
  try {
    await apiFetch("/api/v1/push-tokens", {
      method: "DELETE",
      body: JSON.stringify({ token }),
    });
  } catch {
    // best-effort
  }
  await SecureStore.deleteItemAsync(TOKEN_KEY);
}

/**
 * 알림 핸들러 — 앱이 포그라운드일 때도 alert/sound 표시.
 * RootLayout 에서 한 번 호출.
 */
export function configureNotificationHandler() {
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowAlert: true,
      shouldPlaySound: true,
      shouldSetBadge: false,
      shouldShowBanner: true,
      shouldShowList: true,
    }),
  });
}
