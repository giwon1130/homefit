import { Tabs } from "expo-router";

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: "#2563eb",
        tabBarStyle: { backgroundColor: "#ffffff" },
      }}
    >
      <Tabs.Screen name="index" options={{ title: "청약", tabBarLabel: "청약" }} />
      <Tabs.Screen name="match" options={{ title: "내맞춤", tabBarLabel: "내맞춤" }} />
      <Tabs.Screen name="profile" options={{ title: "프로필", tabBarLabel: "프로필" }} />
    </Tabs>
  );
}
