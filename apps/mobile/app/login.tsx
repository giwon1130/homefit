import { useState } from "react";
import { Platform, Pressable, StyleSheet, Text, View } from "react-native";
import { useRouter } from "expo-router";
import * as AppleAuthentication from "expo-apple-authentication";
import { appleSignIn, useGoogleSignIn } from "@/lib/auth";

export default function LoginScreen() {
  const router = useRouter();
  const [appleBusy, setAppleBusy] = useState(false);

  const onSuccess = () => {
    if (router.canDismiss()) router.dismissAll();
    router.replace("/match");
  };

  const { ready, signIn } = useGoogleSignIn(onSuccess);

  const onApple = async () => {
    setAppleBusy(true);
    const ok = await appleSignIn();
    setAppleBusy(false);
    if (ok) onSuccess();
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>homefit</Text>
      <Text style={styles.subtitle}>
        내 조건에 맞는 청약,{"\n"}출퇴근 가능한 곳만 골라드려요
      </Text>

      {Platform.OS === "ios" && (
        <AppleAuthentication.AppleAuthenticationButton
          buttonType={AppleAuthentication.AppleAuthenticationButtonType.SIGN_IN}
          buttonStyle={AppleAuthentication.AppleAuthenticationButtonStyle.BLACK}
          cornerRadius={10}
          style={[styles.appleBtn, appleBusy && styles.buttonDisabled]}
          onPress={() => {
            if (!appleBusy) void onApple();
          }}
        />
      )}

      <Pressable
        style={[styles.button, !ready && styles.buttonDisabled]}
        onPress={() => ready && signIn()}
        disabled={!ready}
      >
        <Text style={styles.buttonText}>Google 로 계속하기</Text>
      </Pressable>
      <Text style={styles.note}>처음이라면 자동으로 가입돼요. 별도 절차 없음.</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#ffffff",
    alignItems: "center",
    justifyContent: "center",
    padding: 24,
    gap: 16,
  },
  title: { fontSize: 32, fontWeight: "800", color: "#1d4ed8" },
  subtitle: { fontSize: 14, color: "#52525b", textAlign: "center", lineHeight: 22 },
  appleBtn: { width: "100%", height: 48, marginTop: 16 },
  button: {
    backgroundColor: "#ffffff",
    borderWidth: 1,
    borderColor: "#d4d4d8",
    paddingVertical: 14,
    paddingHorizontal: 24,
    borderRadius: 10,
    width: "100%",
    alignItems: "center",
  },
  buttonDisabled: { opacity: 0.5 },
  buttonText: { fontWeight: "600", color: "#27272a", fontSize: 15 },
  note: { fontSize: 12, color: "#a1a1aa", textAlign: "center" },
});
