#!/usr/bin/env python3
"""
TestFlight 첫 빌드용 placeholder 아이콘/스플래시 생성기.

Pure stdlib (zlib + struct) 로 PNG 직접 만든다 — PIL 의존성 없음.
브랜드 색상 #2563eb 단색 + 중앙에 "h" 글자 (간단한 비트맵).

실제 아이콘은 디자이너가 그린 후 동일 경로에 덮어쓰면 됨.
"""

import os
import struct
import zlib

# 브랜드 컬러 — Tailwind blue-600
BRAND_RGB = (0x25, 0x63, 0xEB)
BG_RGB = (0xFA, 0xFA, 0xFA)
WHITE = (0xFF, 0xFF, 0xFF)


def write_png(path: str, pixels: list, w: int, h: int) -> None:
    """pixels: list of (r,g,b) tuples, length w*h. row-major top-to-bottom."""

    def chunk(name: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + name
            + data
            + struct.pack(">I", zlib.crc32(name + data) & 0xFFFFFFFF)
        )

    # PNG signature
    out = bytearray(b"\x89PNG\r\n\x1a\n")

    # IHDR
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)  # 8-bit RGB
    out += chunk(b"IHDR", ihdr)

    # IDAT — filter byte (0) + raw RGB per row
    raw = bytearray()
    for y in range(h):
        raw.append(0)  # no filter
        for x in range(w):
            r, g, b = pixels[y * w + x]
            raw.extend([r, g, b])
    idat = zlib.compress(bytes(raw), 9)
    out += chunk(b"IDAT", idat)

    out += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(bytes(out))


# 5x7 비트맵 폰트 — 간단한 "h" 형태
H_GLYPH = [
    "1.....1",
    "1.....1",
    "1.....1",
    "1.111.1",
    "11...11",
    "1.....1",
    "1.....1",
]


def make_icon(size: int = 1024, bg=BRAND_RGB, fg=WHITE) -> list:
    """size×size 단색 + 중앙에 'h' 글자 (스케일 조정)."""
    pixels = [bg] * (size * size)
    # 글자 영역: 가운데 ~50% 크기
    glyph_h = size // 2
    cell = glyph_h // len(H_GLYPH)
    glyph_w = cell * len(H_GLYPH[0])
    start_x = (size - glyph_w) // 2
    start_y = (size - glyph_h) // 2

    for gy, row in enumerate(H_GLYPH):
        for gx, ch in enumerate(row):
            if ch != "1":
                continue
            for dy in range(cell):
                for dx in range(cell):
                    px = start_x + gx * cell + dx
                    py = start_y + gy * cell + dy
                    if 0 <= px < size and 0 <= py < size:
                        pixels[py * size + px] = fg
    return pixels


def make_splash(size: int = 2048) -> list:
    """배경 흰색 + 중앙에 더 작은 브랜드 마크."""
    pixels = [BG_RGB] * (size * size)
    glyph_h = size // 4
    cell = glyph_h // len(H_GLYPH)
    glyph_w = cell * len(H_GLYPH[0])
    start_x = (size - glyph_w) // 2
    start_y = (size - glyph_h) // 2
    for gy, row in enumerate(H_GLYPH):
        for gx, ch in enumerate(row):
            if ch != "1":
                continue
            for dy in range(cell):
                for dx in range(cell):
                    px = start_x + gx * cell + dx
                    py = start_y + gy * cell + dy
                    if 0 <= px < size and 0 <= py < size:
                        pixels[py * size + px] = BRAND_RGB
    return pixels


def main():
    out_dir = os.path.dirname(os.path.abspath(__file__))
    print("Generating icon.png (1024x1024)...")
    write_png(os.path.join(out_dir, "icon.png"), make_icon(1024), 1024, 1024)
    print("Generating adaptive-icon.png (1024x1024)...")
    write_png(os.path.join(out_dir, "adaptive-icon.png"), make_icon(1024), 1024, 1024)
    print("Generating splash.png (2048x2048)...")
    write_png(os.path.join(out_dir, "splash.png"), make_splash(2048), 2048, 2048)
    print("Generating notification-icon.png (96x96, monochrome white on transparent — simplified to white solid)...")
    # Android notification icon — Expo notifications plugin 권장 96x96.
    # 실제로는 alpha 채널 필요하지만 placeholder 라 단색 흰 사각형으로.
    write_png(
        os.path.join(out_dir, "notification-icon.png"),
        [WHITE] * (96 * 96),
        96,
        96,
    )
    print("Done.")


if __name__ == "__main__":
    main()
