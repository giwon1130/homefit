import { redirect } from "next/navigation";
import { auth } from "@/auth";
import AdminCurationForm from "./AdminCurationForm";

export const dynamic = "force-dynamic";
export const metadata = { title: "homefit · 큐레이션 관리" };

export default async function AdminPage() {
  const session = await auth();
  if (!session?.accessToken) redirect("/login?callbackUrl=/admin");

  return (
    <div className="space-y-4">
      <header>
        <h1 className="text-2xl font-bold">단지 큐레이션</h1>
        <p className="mt-1 text-sm text-zinc-500">
          데이터가 부족한 청약 단지의 주소·좌표·세대수를 수동으로 보강합니다.
          <br />
          ⚠ 변경은 모든 사용자에게 즉시 반영됩니다.
        </p>
      </header>
      <AdminCurationForm />
    </div>
  );
}
