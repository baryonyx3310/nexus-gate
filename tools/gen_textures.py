"""Generates every Nexus Gate texture procedurally (stdlib only).

Visual language: futuristic machinery drawn as Minecraft pixel art. Graphite frame, off-white ceramic armour,
cyan energy used sparingly, amber status lights. Every material uses a short ramp of 3-5 flat tones with a
1px bevel (light top/left, shadow bottom/right) and sparse ordered noise, like vanilla metal blocks.

Resolution follows complexity: simple surfaces are 16x16 (vanilla texel size), detailed panels 32x32.
Particles are tiny crisp sprites (8x8) and a 32x32 stepped shock ring.

Run from the project root:  python tools/gen_textures.py
"""
import math
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "nexusgate", "textures")

# ----------------------------------------------------------------------------- palette
T = (0, 0, 0, 0)
G0 = (0x0C, 0x0F, 0x12)   # graphite deepest
G1 = (0x16, 0x19, 0x1E)   # graphite dark
G2 = (0x2A, 0x2F, 0x37)   # graphite
G3 = (0x3C, 0x43, 0x4D)   # steel
G4 = (0x6B, 0x73, 0x80)   # worn edge
G5 = (0x9A, 0xA3, 0xAE)   # highlight
CE0 = (0xA8, 0xAC, 0xA6)  # ceramic shadow
CE1 = (0xD9, 0xDC, 0xD6)  # ceramic
CE2 = (0xF2, 0xF4, 0xEE)  # ceramic light
CY0 = (0x0A, 0x6E, 0x80)
CY1 = (0x33, 0xF0, 0xFF)
CY2 = (0xCF, 0xFB, 0xFF)
AM1 = (0xFF, 0xB3, 0x47)
AM0 = (0xA0, 0x60, 0x18)
AM2 = (0xFF, 0xE6, 0xB0)
HZ = (0xE0, 0xA8, 0x30)   # hazard stripe

# Legacy 16x16 palette (locator item).
M0 = (0x14, 0x17, 0x1C, 255)
M1 = (0x2B, 0x2F, 0x36, 255)
M2 = (0x3C, 0x42, 0x4B, 255)
M3 = (0x55, 0x5D, 0x69, 255)
RV = (0x7A, 0x84, 0x94, 255)
C1 = (0x00, 0xE5, 0xFF, 255)
C2 = (0x7F, 0xF6, 0xFF, 255)
C0 = (0x00, 0x8C, 0xA0, 255)
RD = (0xD0, 0x30, 0x30, 255)
GD = (0xFF, 0xC9, 0x4A, 255)


# ----------------------------------------------------------------------------- png io
def write_png(path, pixels):
    h = len(pixels)
    w = len(pixels[0])
    rows = []
    for row in pixels:
        line = bytearray(b"\x00")
        for px in row:
            if len(px) == 3:
                px = (px[0], px[1], px[2], 255)
            line += bytes(max(0, min(255, int(round(c)))) for c in px)
        rows.append(bytes(line))
    raw = b"".join(rows)

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


# ----------------------------------------------------------------------------- helpers
def hash2(x, y, seed=0):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def mixc(c0, c1, t):
    t = max(0.0, min(1.0, t))
    return tuple(c0[i] + (c1[i] - c0[i]) * t for i in range(3))


def canvas(n, fill):
    return [[tuple(fill) for _ in range(n)] for _ in range(n)]


def speckle(g, base, lo, hi, seed, p_lo=0.10, p_hi=0.08, x0=0, y0=0, x1=None, y1=None):
    """Flat fill with sparse single-pixel darker/lighter speckles (vanilla metal look)."""
    n = len(g)
    x1 = n - 1 if x1 is None else x1
    y1 = n - 1 if y1 is None else y1
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            h = hash2(x, y, seed)
            g[y][x] = lo if h < p_lo else hi if h > 1.0 - p_hi else base


def rect(g, x0, y0, x1, y1, c):
    for y in range(max(0, y0), min(len(g), y1 + 1)):
        for x in range(max(0, x0), min(len(g[0]), x1 + 1)):
            g[y][x] = tuple(c)


def bevel(g, x0, y0, x1, y1, light, dark):
    """1px bevel: lit top/left, shadowed bottom/right."""
    for x in range(x0, x1 + 1):
        g[y0][x] = light
        g[y1][x] = dark
    for y in range(y0, y1 + 1):
        g[y][x0] = light
        g[y][x1] = dark


def bolt(g, x, y):
    """2x2 rivet."""
    g[y][x] = G5
    g[y][x + 1] = G4
    g[y + 1][x] = G4
    g[y + 1][x + 1] = G0


# ----------------------------------------------------------------------------- block textures (16x16)
def frame_graphite():
    g = canvas(16, G2)
    speckle(g, G2, G1, G3, 11)
    bevel(g, 0, 0, 15, 15, G4, G0)
    # panel seam across the middle
    for x in range(1, 15):
        g[7][x] = G0
        g[8][x] = G3
    for (x, y) in ((2, 2), (12, 2), (2, 11), (12, 11)):
        bolt(g, x, y)
    # stencil ticks
    for x in (6, 8):
        g[11][x] = G4
        g[12][x] = G3
    return g


def armor_ceramic():
    g = canvas(16, CE1)
    speckle(g, CE1, CE0, CE2, 5, 0.07, 0.07)
    bevel(g, 0, 0, 15, 15, CE2, CE0)
    # plate seams
    for x in range(1, 15):
        g[5][x] = CE0
        g[6][x] = CE2
    for y in range(7, 15):
        g[y][10] = CE0
        g[y][11] = CE2
    # stencil ticks (quiet detail; hazard colour now lives only on the plinth band)
    g[2][2] = g[2][4] = g[2][6] = CE0
    return g


def vent_grille():
    g = canvas(16, G2)
    speckle(g, G2, G1, G3, 21)
    bevel(g, 0, 0, 15, 15, G4, G0)
    rect(g, 2, 2, 13, 13, G1)
    bevel(g, 2, 2, 13, 13, G0, G3)
    for y in (4, 7, 10):
        for x in range(3, 13):
            g[y][x] = G0
            g[y + 1][x] = CY0 if 6 <= x <= 9 else G0
    return g


def heatsink():
    g = canvas(16, G1)
    for y in range(16):
        for x in range(16):
            g[y][x] = G3 if x % 2 == 0 else G0
    for x in range(16):
        g[0][x] = G4
        g[15][x] = G0
        g[7][x] = G0
        g[8][x] = G4 if x % 2 == 0 else G2
    return g


def plinth_top():
    g = canvas(16, G2)
    speckle(g, G2, G1, G3, 51, 0.06, 0.04)
    # tread studs
    for y in range(3, 13, 3):
        for x in range(3, 13, 3):
            g[y][x] = G4
    bevel(g, 0, 0, 15, 15, G4, G0)
    # emitter pad: stepped pixel ring
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 4.6 < d <= 5.6:
                g[y][x] = G0
            elif 3.0 < d <= 4.6:
                ang = math.atan2(y - 7.5, x - 7.5)
                g[y][x] = CY1 if int((ang + math.pi) / (math.pi / 4)) % 2 == 0 else CY0
            elif d <= 3.0:
                g[y][x] = G1 if (x + y) % 2 else G0
    return g


def hazard_stripe():
    """Seamless 45-degree hazard stripes: 4px period (2px yellow, 2px black), tiling in both directions."""
    hz_lo = mixc(HZ, G0, 0.25)
    g = canvas(16, G0)
    for y in range(16):
        for x in range(16):
            on = ((x + y) // 2) % 2 == 0
            # yellow gets a 1px darker trailing edge so the stripes read crisp at a distance
            if on:
                g[y][x] = hz_lo if (x + y) % 2 == 1 else HZ
            else:
                g[y][x] = G0 if (x + y) % 2 == 1 else G1
    return g


def cable():
    """Rubber cable: dark jacket, 1px highlight column, sleeve bands every 4px."""
    g = canvas(16, G1)
    for y in range(16):
        for x in range(16):
            c = G2 if x % 16 in (4, 5) else G1
            if y % 4 == 3:
                c = G0
            elif y % 4 == 0:
                c = G3 if x % 16 in (4, 5) else G2
            g[y][x] = c
    return g


def screen():
    """Unlit display glass for the crown name screen: near-black with faint cyan scanlines."""
    g = canvas(16, (0x05, 0x08, 0x0A))
    for y in range(16):
        for x in range(16):
            g[y][x] = (0x08, 0x12, 0x16) if y % 2 else (0x05, 0x08, 0x0A)
    return g


def drum_dark():
    g = canvas(16, G1)
    speckle(g, G1, G0, G2, 71, 0.18, 0.10)
    return g


def glow_strip(hi, mid, lo):
    """Segmented LED bar: three flat bands, a dark divider every 4px."""
    g = canvas(16, lo)
    for y in range(16):
        d = abs(y - 7.5)
        c = hi if d < 2 else mid if d < 5 else lo
        for x in range(16):
            g[y][x] = mixc(c, (0, 0, 0), 0.35) if x % 4 == 3 else c
    return g


# ----------------------------------------------------------------------------- block textures (32x32)
def machinery():
    g = canvas(32, G1)
    speckle(g, G1, G0, G2, 41, 0.08, 0.06)
    bevel(g, 0, 0, 31, 31, G3, G0)
    # horizontal pipe (2px blocks: light, mid, dark)
    for x in range(2, 30):
        g[4][x] = G4
        g[5][x] = G4
        g[6][x] = G3
        g[7][x] = G3
        g[8][x] = G0
    for x in range(6, 30, 8):  # pipe clamps
        for y in range(3, 9):
            g[y][x] = G5 if y == 3 else G2
    # vertical pipe
    for y in range(9, 30):
        g[y][24] = G4
        g[y][25] = G4
        g[y][26] = G3
        g[y][27] = G0
    # sub-panels
    rect(g, 3, 11, 13, 20, G2)
    bevel(g, 3, 11, 13, 20, G3, G0)
    for y in range(13, 19, 2):
        for x in range(5, 12):
            g[y][x] = G0
    rect(g, 15, 11, 21, 28, G2)
    bevel(g, 15, 11, 21, 28, G3, G0)
    # indicator column, 2x2 LEDs
    for i, y in enumerate(range(13, 27, 3)):
        c = AM1 if i == 0 else CY1 if i < 3 else G0
        rect(g, 17, y, 18, y + 1, c)
        if c != G0:
            g[y][17] = AM2 if c == AM1 else CY2
    # ceramic tag
    rect(g, 4, 23, 12, 27, CE1)
    bevel(g, 4, 23, 12, 27, CE2, CE0)
    for x in range(6, 11, 2):
        g[25][x] = G2
    bolt(g, 2, 28)
    bolt(g, 28, 28)
    return g


def drum_metal():
    """Gunmetal plate for the rotating drum and chamber cartridges (pixel-UV mapped by the renderer)."""
    g = canvas(32, G3)
    speckle(g, G3, G2, G4, 61, 0.10, 0.08)
    bevel(g, 0, 0, 31, 31, G5, G1)
    # machined grooves
    for y in (8, 16, 24):
        for x in range(1, 31):
            g[y][x] = G1
            g[y + 1][x] = G4
    # corner rivets
    for (x, y) in ((2, 2), (28, 2), (2, 28), (28, 28)):
        bolt(g, x, y)
    return g


def white():
    return canvas(16, (255, 255, 255))


# ----------------------------------------------------------------------------- particles
def alpha_grid(n):
    return [[T for _ in range(n)] for _ in range(n)]


def set_a(g, x, y, a):
    g[y][x] = (255, 255, 255, a)


def p_mote():
    """8x8 glowing pixel: 2x2 core, 1px inner halo, sparse outer halo."""
    g = alpha_grid(8)
    for y in range(8):
        for x in range(8):
            d = max(abs(x - 3.5), abs(y - 3.5))
            if d <= 1:
                set_a(g, x, y, 255)
            elif d <= 2:
                set_a(g, x, y, 150)
            elif d <= 3 and (abs(x - 3.5) < 2 or abs(y - 3.5) < 2):
                set_a(g, x, y, 55)
    return g


def p_spark():
    """8x8 pixel bar (stretched along velocity by the renderer)."""
    g = alpha_grid(8)
    for x in range(1, 7):
        set_a(g, x, 3, 255)
        set_a(g, x, 4, 255)
    for x in range(2, 6):
        set_a(g, x, 2, 90)
        set_a(g, x, 5, 90)
    return g


def p_shard():
    """8x8 tiny cube: bright rim, lit top-left face, darker body."""
    g = alpha_grid(8)
    for y in range(1, 7):
        for x in range(1, 7):
            edge = x in (1, 6) or y in (1, 6)
            set_a(g, x, y, 255 if edge else 150)
    for (x, y) in ((2, 2), (3, 2), (2, 3)):
        set_a(g, x, y, 235)
    return g


def p_quantum():
    """8x8 square pixel of converted matter: solid 6x6 body, faint 1px rim."""
    g = alpha_grid(8)
    for y in range(8):
        for x in range(8):
            rim = x in (0, 7) or y in (0, 7)
            set_a(g, x, y, 110 if rim else 255)
    return g


def p_ring():
    """32x32 octagonal ring with a fainter inner trace (pressure wave)."""
    n = 32
    g = alpha_grid(n)
    c = (n - 1) / 2.0
    for y in range(n):
        for x in range(n):
            dx, dy = abs(x - c), abs(y - c)
            d = max(dx, dy, (dx + dy) * 0.7071)
            if 12.5 <= d < 14.5:
                set_a(g, x, y, 255)
            elif 9.5 <= d < 10.5:
                set_a(g, x, y, 100)
    return g


def grid(fill):
    return [[fill for _ in range(16)] for _ in range(16)]


def locator():
    g = grid(T)
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
    for y in range(3, 10):
        for x in range(5, 11):
            g[y][x] = M0
    g[4][7] = g[4][8] = C2
    for x in range(6, 10):
        g[5][x] = C1
    for y in range(6, 9):
        g[y][7] = g[y][8] = C1
    g[11][5] = RD
    g[11][7] = C1
    g[11][9] = GD
    g[0][10] = RV
    g[0][11] = T
    return g


def main():
    block = os.path.join(ROOT, "block")
    item = os.path.join(ROOT, "item")
    particle = os.path.join(ROOT, "particle")
    for name in ("station_base", "station_side", "station_core", "station_top"):
        old = os.path.join(block, name + ".png")
        if os.path.exists(old):
            os.remove(old)
    textures = {
        # 16x16: simple surfaces
        "frame_graphite": frame_graphite(),
        "armor_ceramic": armor_ceramic(),
        "vent_grille": vent_grille(),
        "heatsink": heatsink(),
        "plinth_top": plinth_top(),
        "hazard_stripe": hazard_stripe(),
        "cable": cable(),
        "screen": screen(),
        "drum_dark": drum_dark(),
        "glow_cyan": glow_strip(CY2, CY1, CY0),
        "glow_amber": glow_strip(AM2, AM1, AM0),
        "white": white(),
        # 32x32: detailed panels
        "machinery": machinery(),
        "drum_metal": drum_metal(),
    }
    for name, px in textures.items():
        write_png(os.path.join(block, name + ".png"), px)
    write_png(os.path.join(item, "locator.png"), locator())
    write_png(os.path.join(particle, "mote.png"), p_mote())
    write_png(os.path.join(particle, "spark.png"), p_spark())
    write_png(os.path.join(particle, "shard.png"), p_shard())
    write_png(os.path.join(particle, "shock_ring.png"), p_ring())
    write_png(os.path.join(particle, "quantum.png"), p_quantum())
    print("textures written to", os.path.abspath(ROOT))


if __name__ == "__main__":
    main()
