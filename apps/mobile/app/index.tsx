import { StyleSheet, Text, View } from "react-native";

export default function Index() {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>homefit</Text>
      <Text style={styles.subtitle}>Phase 0 — 스캐폴딩 완료</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    padding: 24,
  },
  title: {
    fontSize: 32,
    fontWeight: "700",
  },
  subtitle: {
    fontSize: 14,
    color: "#666",
    marginTop: 8,
  },
});
