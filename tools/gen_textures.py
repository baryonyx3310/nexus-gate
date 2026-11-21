"""Generates the original 16x16 textures for Nexus Gate (stdlib only).

Run from the project root:  python tools/gen_textures.py
"""
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "nexusgate", "textures")

# Palette
T = (0, 0, 0, 0)
M0 = (0x14, 0x17, 0x1C, 255)   # dark slot
M1 = (0x2B, 0x2F, 0x36, 255)   # metal dark
M2 = (0x3C, 0x42, 0x4B, 255)   # metal mid
M3 = (0x55, 0x5D, 0x69, 255)   # metal light
RV = (0x7A, 0x84, 0x94, 255)   # rivet / highlight
C1 = (0x00, 0xE5, 0xFF, 255)   # cyan
C2 = (0x7F, 0xF6, 0xFF, 255)   # cyan bright
C0 = (0x00, 0x8C, 0xA0, 255)   # cyan dim
RD = (0xD0, 0x30, 0x30, 255)   # red
GD = (0xFF, 0xC9, 0x4A, 255)   # gold


def write_png(path, pixels):
    h = len(pixels)
    w = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in pixels)

    def chunk(kind, data):
        c = kind + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def grid(fill) -> "list[list[tuple[int, int, int, int]]]":
    return [[fill for _ in range(16)] for _ in range(16)]


def frame(g, color, inner=None):
    for i in range(16):
        g[0][i] = g[15][i] = g[i][0] = g[i][15] = color
    if inner:
        for i in range(1, 15):
            g[1][i] = g[i][1] = inner
            g[14][i] = g[i][14] = M0


def rivets(g, spots):
    for (x, y) in spots:
        g[y][x] = RV


def station_base():
    g = grid(M2)
    frame(g, M1, M3)
    # horizontal panel seams
    for x in range(2, 14):
        g[5][x] = M1
        g[10][x] = M1
    # vent slots
    for y in (7, 8):
        for x in range(4, 12, 2):
            g[y][x] = M0
    rivets(g, [(2, 2), (13, 2), (2, 13), (13, 13)])
    # status light
    g[12][7] = C1
    g[12][8] = C0
    return g


def station_side():
    g = grid(M2)
    frame(g, M1, M3)
    # vertical rails
    for y in range(2, 14):
        g[y][4] = M1
        g[y][11] = M1
    # small panel dots
    rivets(g, [(2, 3), (13, 3), (2, 12), (13, 12)])
    for y in range(3, 13, 3):
        g[y][7] = M3
        g[y][8] = M3
    return g


def station_core():
    g = grid(M1)
    for y in range(16):
        g[y][0] = g[y][15] = M0
        g[y][1] = g[y][14] = M2
    # glowing core with pulse pattern
    for y in range(16):
        for x in range(4, 12):
            g[y][x] = C1
        g[y][3] = g[y][12] = C0
    for y in range(1, 16, 4):
        for x in range(5, 11):
            g[y][x] = C2
    for y in range(3, 16, 4):
        g[y][7] = g[y][8] = (0xE0, 0xFF, 0xFF, 255)
    return g


def station_top():
    g = grid(M2)
    frame(g, M1, M3)
    # concentric emitter
    for i in range(4, 12):
        g[4][i] = g[11][i] = g[i][4] = g[i][11] = M1
    for y in range(5, 11):
        for x in range(5, 11):
            g[y][x] = C0
    for y in range(6, 10):
        for x in range(6, 10):
            g[y][x] = C1
    g[7][7] = g[7][8] = g[8][7] = g[8][8] = C2
    rivets(g, [(2, 2), (13, 2), (2, 13), (13, 13)])
    return g


def locator():
    g = grid(T)
    # handheld device body (rounded rectangle 3..12 x 1..14)
    for y in range(1, 15):
        for x in range(3, 13):
            g[y][x] = M2
    for x in range(4, 12):
        g[1][x] = M3
        g[14][x] = M1
    g[1][3] = g[1][12] = g[14][3] = g[14][12] = T
    for y in range(2, 14):
        g[y][3] = M3
        g[y][12] = M1
    # screen
    for y in range(3, 10):
        for x in range(5, 11):
            g[y][x] = M0
    # arrow on screen
    g[4][7] = g[4][8] = C2
    for x in range(6, 10):
        g[5][x] = C1
    for y in range(6, 9):
        g[y][7] = g[y][8] = C1
    # buttons
    g[11][5] = RD
    g[11][7] = C1
    g[11][9] = GD
    # antenna
    g[0][10] = RV
    g[0][11] = T
    return g


def main():
    block = os.path.join(ROOT, "block")
    item = os.path.join(ROOT, "item")
    write_png(os.path.join(block, "station_base.png"), station_base())
    write_png(os.path.join(block, "station_side.png"), station_side())
    write_png(os.path.join(block, "station_core.png"), station_core())
    write_png(os.path.join(block, "station_top.png"), station_top())
    write_png(os.path.join(item, "locator.png"), locator())
    print("textures written to", os.path.abspath(ROOT))


if __name__ == "__main__":
    main()
