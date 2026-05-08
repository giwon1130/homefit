import { useEffect } from "react";
import { Stack, useRouter } from "expo-router";
import * as Notifications from "expo-notifications";
import { configureNotificationHandler } from "@/lib/push";
import { initSentry } from "@/lib/sentry";

initSentry();
configureNotificationHandler();

export default function RootLayout() {
  const router = useRouter();

  // 사용자가 푸시 알림을 탭했을 때: data.listingId 가 있으면 단지 상세로 이동.
  useEffect(() => {
    const sub = Notifications.addNotificationResponseReceivedListener((response) => {
      const data = response.notification.request.content.data as {
        listingId?: number;
        deepLink?: string;
      };
      if (data?.listingId) {
        router.push({
          pathname: "/listings/[id]",
          params: { id: String(data.listingId) },
        });
      }
    });
    return () => sub.remove();
  }, [router]);

  return (
    <Stack screenOptions={{ headerStyle: { backgroundColor: "#fafafa" } }}>
      <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
      <Stack.Screen name="listings/[id]" options={{ title: "상세", headerBackTitle: "뒤로" }} />
      <Stack.Screen name="profile/edit" options={{ title: "프로필 편집", headerBackTitle: "뒤로" }} />
      <Stack.Screen name="favorites/compare" options={{ title: "즐겨찾기 비교", headerBackTitle: "뒤로" }} />
      <Stack.Screen name="login" options={{ title: "로그인", presentation: "modal" }} />
    </Stack>
  );
}
