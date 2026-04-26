import { redirect } from "next/navigation";
import { auth } from "@/auth";
import { apiFetch, type FullProfile } from "@/lib/api";
import ProfileForm from "../profile/ProfileForm";
import { isProfileEmpty } from "@/lib/profile";

export const dynamic = "force-dynamic";

export default async function OnboardingPage() {
  const session = await auth();
  if (!session?.accessToken) redirect("/login?callbackUrl=/onboarding");

  const res = await apiFetch("/api/v1/profile");
  if (!res.ok) {
    return <div className="text-red-600">프로필 조회 실패 (HTTP {res.status})</div>;
  }
  const profile: FullProfile = await res.json();

  // 이미 정보가 어느 정도 들어가있으면 바로 매칭으로
  if (!isProfileEmpty(profile)) redirect("/match");

  return (
    <div className="space-y-6">
      <header className="space-y-2 rounded-lg border border-blue-200 bg-blue-50 p-4">
        <h1 className="text-xl font-bold">환영합니다 👋</h1>
        <p className="text-sm text-zinc-700">
          몇 가지 정보를 알려주시면 자격에 맞고 통근 가능한 청약만 골라서 보여드려요.
          모든 항목은 선택이며, 언제든 프로필에서 수정할 수 있어요.
        </p>
      </header>

      <ProfileForm
        initialCore={profile.core}
        initialMembers={profile.householdMembers}
        initialWorkplaces={profile.workplaces}
        initialPreferences={profile.preferences}
        onSavedRedirectTo="/match"
        showSkipLink={{ href: "/match", label: "건너뛰고 둘러보기 →" }}
        saveLabel="저장하고 매칭 보기"
      />
    </div>
  );
}
