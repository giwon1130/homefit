import * as SecureStore from "expo-secure-store";
import * as Google from "expo-auth-session/providers/google";
import * as WebBrowser from "expo-web-browser";
import Constants from "expo-constants";
import { useEffect } from "react";

WebBrowser.maybeCompleteAuthSession();

const ACCESS_KEY = "homefit.accessToken";
const REFRESH_KEY = "homefit.refreshToken";
const USER_KEY = "homefit.user";

const API_BASE: string =
  (Constants.expoConfig?.extra as { apiBaseUrl?: string } | undefined)?.apiBaseUrl ??
  process.env.EXPO_PUBLIC_API_BASE_URL ??
  "https://api-production-1d45.up.railway.app";

const GOOGLE_WEB_CLIENT_ID =
  (Constants.expoConfig?.extra as { googleClientId?: string } | undefined)?.googleClientId ??
  process.env.EXPO_PUBLIC_GOOGLE_OAUTH_CLIENT_ID ??
  "";

export async function getAccessToken(): Promise<string | null> {
  return (await SecureStore.getItemAsync(ACCESS_KEY)) ?? null;
}

export async function clearTokens(): Promise<void> {
  await SecureStore.deleteItemAsync(ACCESS_KEY);
  await SecureStore.deleteItemAsync(REFRESH_KEY);
  await SecureStore.deleteItemAsync(USER_KEY);
}

export async function isLoggedIn(): Promise<boolean> {
  return (await getAccessToken()) !== null;
}

/**
 * Google OAuth 로그인 후 백엔드 토큰 교환.
 * useGoogleSignIn() 훅 형태로 expo-auth-session 의 useAuthRequest 를 래핑.
 */
export function useGoogleSignIn(onSuccess?: () => void) {
  const [request, response, promptAsync] = Google.useAuthRequest({
    clientId: GOOGLE_WEB_CLIENT_ID,
    iosClientId: GOOGLE_WEB_CLIENT_ID,
    androidClientId: GOOGLE_WEB_CLIENT_ID,
  });

  useEffect(() => {
    (async () => {
      if (response?.type !== "success") return;
      const idToken = response.authentication?.idToken;
      if (!idToken) return;
      const res = await fetch(`${API_BASE}/api/v1/auth/google`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ idToken }),
      });
      if (!res.ok) {
        console.warn("backend exchange failed", res.status);
        return;
      }
      const data = (await res.json()) as {
        accessToken: string;
        refreshToken: string;
        user: { id: number; email: string; displayName?: string };
      };
      await SecureStore.setItemAsync(ACCESS_KEY, data.accessToken);
      await SecureStore.setItemAsync(REFRESH_KEY, data.refreshToken);
      await SecureStore.setItemAsync(USER_KEY, JSON.stringify(data.user));
      onSuccess?.();
    })();
  }, [response, onSuccess]);

  return {
    ready: !!request,
    signIn: () => promptAsync(),
  };
}

export async function getStoredUser(): Promise<{ id: number; email: string; displayName?: string } | null> {
  const raw = await SecureStore.getItemAsync(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}
