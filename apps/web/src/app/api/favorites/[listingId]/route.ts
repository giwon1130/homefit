import { NextResponse } from "next/server";
import { auth } from "@/auth";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

async function proxy(method: string, listingId: string) {
  const session = await auth();
  if (!session?.accessToken) {
    return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  }
  const res = await fetch(`${API_BASE}/api/v1/favorites/${listingId}`, {
    method,
    headers: { Authorization: `Bearer ${session.accessToken}` },
  });
  return new NextResponse(res.body, {
    status: res.status,
    headers: { "Content-Type": "application/json" },
  });
}

export async function PUT(_req: Request, ctx: { params: Promise<{ listingId: string }> }) {
  const { listingId } = await ctx.params;
  return proxy("PUT", listingId);
}

export async function DELETE(_req: Request, ctx: { params: Promise<{ listingId: string }> }) {
  const { listingId } = await ctx.params;
  return proxy("DELETE", listingId);
}
