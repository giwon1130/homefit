import { useCallback, useState } from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";
import { useFocusEffect, useRouter } from "expo-router";
import { clearTokens, getStoredUser, isLoggedIn } from "@/lib/auth";

export default function ProfileTab() {
  const router = useRouter();
  const [user, setUser] = useState<{ id: number; email: string; displayName?: string } | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    if (await isLoggedIn()) {
      setUser(await getStoredUser());
    } else {
      setUser(null);
    }
    setLoading(false);
  }, []);

  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  if (loading) {
    return (
      <View style={[styles.container, styles.center]}>
        <ActivityIndicator />
      </View>
    );
  }

  if (!user) {
    return (
      <View style={[styles.container, styles.center]}>
        <Text style={styles.title}>homefit</Text>
        <Text style={styles.muted}>로그인하면 프로필 입력 / 매칭 / 즐겨찾기를 사용할 수 있어요.</Text>
        <Pressable style={styles.button} onPress={() => router.push("/login")}>
          <Text style={styles.buttonText}>Google 로 로그인</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.card}>
        <Text style={styles.title}>{user.displayName ?? user.email}</Text>
        <Text style={styles.muted}>{user.email}</Text>
      </View>

      <Pressable style={styles.linkRow} onPress={() => router.push("/profile/edit")}>
        <View style={{ flex: 1 }}>
          <Text style={styles.linkLabel}>프로필 편집</Text>
          <Text style={styles.linkSub}>가족 / 직장 / 소득 / 자산 / 예산</Text>
        </View>
        <Text style={styles.chevron}>›</Text>
      </Pressable>

      <View style={styles.card}>
        <Text style={styles.muted}>
          정확한 매칭을 위해 직장 주소와 예산을 채워주세요.{"\n"}
          소득·자산은 암호화 저장됩니다.
        </Text>
      </View>

      <Pressable
        style={[styles.button, styles.logoutBtn]}
        onPress={async () => {
          await clearTokens();
          router.replace("/");
        }}
      >
        <Text style={styles.buttonText}>로그아웃</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#fafafa", paddingTop: 16, gap: 12 },
  center: { flex: 1, alignItems: "center", justifyContent: "center", padding: 32, gap: 12 },
  card: {
    backgroundColor: "white",
    padding: 20,
    marginHorizontal: 16,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: "#e4e4e7",
  },
  linkRow: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "white",
    paddingHorizontal: 20,
    paddingVertical: 16,
    marginHorizontal: 16,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: "#e4e4e7",
  },
  linkLabel: { fontSize: 15, fontWeight: "600", color: "#0a0a0a" },
  linkSub: { fontSize: 12, color: "#71717a", marginTop: 2 },
  chevron: { fontSize: 24, color: "#a1a1aa" },
  title: { fontSize: 20, fontWeight: "700" },
  muted: { fontSize: 14, color: "#71717a", marginTop: 4 },
  button: {
    backgroundColor: "#2563eb",
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
    marginTop: 12,
    alignItems: "center",
  },
  logoutBtn: { backgroundColor: "#dc2626", marginHorizontal: 16 },
  buttonText: { color: "white", fontWeight: "600" },
});
