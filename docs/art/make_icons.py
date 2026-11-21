#!/usr/bin/env python3
"""Dire Difficulty mod-icon drafts, family style of Skill Proficiencies / Archetypes:
dark-navy rounded square with a lighter rim, chunky pixel art on a 32 grid, NEAREST x16.

Writes docs/art/icons/<name>.png (512) and docs/art/icons/sheet.png (all drafts side by side).
Run: python3 docs/art/make_icons.py
"""
import math
import os

from PIL import Image, ImageDraw

SIZE = 512
GRID = 32
FILL = (34, 36, 46)
BORDER = (64, 68, 84)
INSET, BORDER_W, RADIUS = 8, 6, 96

OUT = (14, 14, 20)
BONE = (226, 222, 204)
BONE_S = (170, 164, 146)
BONE_D = (118, 112, 100)
SOCKET = (26, 20, 24)
RED = (255, 58, 58)
RED_HOT = (255, 196, 170)
RED_D = (150, 30, 34)
GOLD = (255, 200, 70)
GOLD_S = (196, 132, 30)
RARITY = [(85, 255, 85), (85, 170, 255), (170, 85, 255), (255, 170, 0), (255, 58, 58)]

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, "icons")

SKULL = [
    "....########....",
    "..##wwwwwwww##..",
    ".#wwwwwwwwwwws#.",
    "#wwwwwwwwwwwwws#",
    "#wwwwwwwwwwwwws#",
    "#ww####ww####ws#",
    "#w#rrh#ww#hrr#s#",
    "#w#rrr#ww#rrr#s#",
    "#ww###wwww###ws#",
    ".#wwwww##wwwws#.",
    ".#swwww##wwwss#.",
    "..#swwwwwwwws#..",
    "..#w#w#ww#w#w#..",
    "...#d#d##d#d#...",
    "....########....",
]
SKULL_COLOURS = {"#": OUT, "w": BONE, "s": BONE_S, "d": BONE_D, "r": RED, "h": RED_HOT}


def blank():
    return Image.new("RGBA", (GRID, GRID), (0, 0, 0, 0))


def put(img, x, y, c):
    if 0 <= x < GRID and 0 <= y < GRID:
        img.putpixel((x, y), tuple(c[:3]) + (255,))


def blend(a, b, t):
    return tuple(round(a[i] * (1 - t) + b[i] * t) for i in range(3))


def sprite(img, rows, colours, ox, oy):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in colours:
                put(img, ox + x, oy + y, colours[ch])


def glow(img, cx, cy, radius, colour, strength=0.75, steps=3):
    """Pixel aura: a few crisp rings, drawn only on empty pixels, kept inside the tile."""
    for y in range(2, GRID - 2):
        for x in range(2, GRID - 2):
            if img.getpixel((x, y))[3]:
                continue
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / radius
            if d >= 1.0:
                continue
            t = math.ceil((1.0 - d) * steps) / steps
            put(img, x, y, blend(FILL, colour, t * strength))


def embers(img, points, colour):
    for x, y in points:
        put(img, x, y, colour)


def crown(img, ox, oy):
    rows = [
        "#..#..#..#",
        "#y#.#y#.#y#"[:10],
        "#yy#yyy#yy#"[:10],
        "#yyyyyyyy#",
        "#gggggggg#",
        "##########",
    ]
    rows = [
        "#...#...#.",
        "#y.#y#.#y#",
        "#yy#y#yyy#",
        "#yyyyyyyy#",
        "#gyggygyg#",
        "##########",
    ]
    rows = [
        "..#...#...#..",
        ".#y#.#y#.#y#.",
        ".#yy#yyy#yy#.",
        ".#yyyyyyyyy#.",
        ".#gygggggyg#.",
        ".###########.",
    ]
    sprite(img, rows, {"#": OUT, "y": GOLD, "g": GOLD_S}, ox, oy)


# ------------------------------------------------------------------ drafts

def crowned_skull():
    img = blank()
    sprite(img, SKULL, SKULL_COLOURS, 8, 11)
    crown(img, 9, 6)
    glow(img, 16, 17, 13, RED, 0.8)
    embers(img, [(5, 10), (26, 8), (4, 22), (27, 21), (23, 4), (8, 4)], RED)
    return img


def skull_and_bar():
    img = blank()
    sprite(img, SKULL, SKULL_COLOURS, 8, 4)
    glow(img, 16, 11, 11, RED, 0.75)
    # elite plate health bar
    for x in range(5, 27):
        put(img, x, 23, OUT)
        put(img, x, 27, OUT)
    for y in range(23, 28):
        put(img, 5, y, OUT)
        put(img, 26, y, OUT)
    for x in range(6, 26):
        for y in range(24, 27):
            put(img, x, y, RED if x < 20 else RED_D)
        put(img, x, 24, RED_HOT if x < 20 else (180, 50, 54))
    return img


def eyes_in_the_dark():
    img = blank()
    hood = [
        ".........##.........",
        ".......##::##.......",
        "......#::::::#......",
        ".....#::::::::#.....",
        "....#::::::::::#....",
        "...#::::::::::::#...",
        "...#::::::::::::#...",
        "..#::::::::::::::#..",
        "..#::::::::::::::#..",
        "..#::::::::::::::#..",
        ".#::::::::::::::::#.",
        ".#::::::::::::::::#.",
        "#::::::::::::::::::#",
        "#::::::::::::::::::#",
        "#::::::::::::::::::#",
        "#::::::::::::::::::#",
        "####################",
    ]
    sprite(img, hood, {"#": (96, 44, 52), ":": (16, 16, 22)}, 6, 8)
    eye = ["rrr", "rhr"]
    sprite(img, eye, {"r": RED, "h": RED_HOT}, 11, 17)
    sprite(img, eye, {"r": RED, "h": RED_HOT}, 18, 17)
    glow(img, 16, 18, 14, RED, 0.7)
    return img


def rarity_ladder():
    img = blank()
    centres = [(7, 25), (11, 21), (15, 17), (19, 12), (24, 7)]
    sizes = [1, 1, 2, 2, 3]
    glow(img, 24, 7, 7, RED, 0.8)
    for (cx, cy), r, colour in zip(centres, sizes, RARITY):
        for dy in range(-r - 1, r + 2):
            for dx in range(-r - 1, r + 2):
                d = abs(dx) + abs(dy)
                if d == r + 1:
                    put(img, cx + dx, cy + dy, OUT)
                elif d <= r:
                    shade = colour if dx + dy < 0 else blend(colour, (0, 0, 0), 0.35)
                    put(img, cx + dx, cy + dy, shade)
        put(img, cx - 1, cy - 1, blend(colour, (255, 255, 255), 0.6))
    return img


DRAFTS = {
    "a_crowned_skull": crowned_skull,
    "b_skull_and_bar": skull_and_bar,
    "c_eyes_in_the_dark": eyes_in_the_dark,
    "d_rarity_ladder": rarity_ladder,
}


def background():
    ss = 4
    s = SIZE * ss
    bg = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(bg)
    d.rounded_rectangle([INSET * ss, INSET * ss, (SIZE - INSET) * ss - 1, (SIZE - INSET) * ss - 1],
                        radius=RADIUS * ss, fill=BORDER + (255,))
    i = (INSET + BORDER_W) * ss
    d.rounded_rectangle([i, i, s - i - 1, s - i - 1], radius=(RADIUS - BORDER_W) * ss, fill=FILL + (255,))
    return bg.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for row in SKULL:
        assert len(row) == 16, row
    tiles = []
    for name, build in DRAFTS.items():
        icon = background()
        icon.alpha_composite(build().resize((SIZE, SIZE), Image.NEAREST))
        icon.save(os.path.join(OUT_DIR, name + ".png"))
        tiles.append(icon)
    sheet = Image.new("RGBA", (len(tiles) * 272 + 16, 288), (24, 24, 30, 255))
    for i, tile in enumerate(tiles):
        sheet.alpha_composite(tile.resize((256, 256), Image.LANCZOS), (16 + i * 272, 16))
    sheet.save(os.path.join(OUT_DIR, "sheet.png"))
    print("wrote", len(tiles), "drafts to", OUT_DIR)


if __name__ == "__main__":
    main()
