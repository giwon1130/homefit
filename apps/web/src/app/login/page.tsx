import { redirect } from "next/navigation";
import { auth } from "@/auth";
import LoginContent from "./LoginContent";

export const dynamic = "force-dynamic";

type Props = { searchParams: Promise<{ callbackUrl?: string }> };

export default async function LoginPage({ searchParams }: Props) {
  const session = await auth();
  if (session?.accessToken) {
    const { callbackUrl } = await searchParams;
    // open-redirect 방어: 사이트 내부 경로만 허용
    const safe =
      callbackUrl && callbackUrl.startsWith("/") && !callbackUrl.startsWith("//")
        ? callbackUrl
        : "/match";
    redirect(safe);
  }
  return <LoginContent />;
}
