"use client";

import { useState, useTransition } from "react";

export default function FavoriteButton({
  listingId,
  initialFavorited,
  authed,
}: {
  listingId: number;
  initialFavorited: boolean;
  authed: boolean;
}) {
  const [favorited, setFavorited] = useState(initialFavorited);
  const [pending, startTransition] = useTransition();

  if (!authed) return null;

  const toggle = () => {
    startTransition(async () => {
      const res = await fetch(`/api/favorites/${listingId}`, {
        method: favorited ? "DELETE" : "PUT",
      });
      if (res.ok) setFavorited(!favorited);
    });
  };

  return (
    <button
      type="button"
      onClick={toggle}
      disabled={pending}
      className={
        favorited
          ? "rounded border border-red-300 bg-red-50 px-3 py-1.5 text-sm text-red-700 disabled:opacity-50"
          : "rounded border border-zinc-300 bg-white px-3 py-1.5 text-sm text-zinc-700 hover:bg-zinc-50 disabled:opacity-50"
      }
    >
      {favorited ? "♥ 즐겨찾기됨" : "♡ 즐겨찾기"}
    </button>
  );
}
