import { Stack } from "expo-router";

export default function RootLayout() {
  return (
    <Stack screenOptions={{ headerStyle: { backgroundColor: "#fafafa" } }}>
      <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
      <Stack.Screen name="listings/[id]" options={{ title: "상세", headerBackTitle: "뒤로" }} />
      <Stack.Screen name="profile/edit" options={{ title: "프로필 편집", headerBackTitle: "뒤로" }} />
      <Stack.Screen name="login" options={{ title: "로그인", presentation: "modal" }} />
    </Stack>
  );
}
