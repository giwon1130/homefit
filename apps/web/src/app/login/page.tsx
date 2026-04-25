import { redirect } from "next/navigation";
import { auth } from "@/auth";
import LoginContent from "./LoginContent";

export const dynamic = "force-dynamic";

type Props = { searchParams: Promise<{ callbackUrl?: string }> };

export default async function LoginPage({ searchParams }: Props) {
  const session = await auth();
  if (session?.accessToken) {
    const { callbackUrl } = await searchParams;
    const safe =
      callbackUrl && callbackUrl.startsWith("/") && !callbackUrl.startsWith("//")
        ? callbackUrl
        : "/onboarding";
    redirect(safe);
  }
  return <LoginContent />;
}
