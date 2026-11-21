#!/usr/bin/env python3
"""Dire Difficulty mod-icon drafts: vanilla mob heads rendered like the item icon (isometric
top / front / side, flat shaded), on the family tile of Skill Proficiencies / Archetypes.

Head textures are read straight from the local Minecraft client jar (loom cache), never
committed. Writes docs/art/icons/<name>.png (512) and docs/art/icons/sheet.png.
Run: python3 docs/art/make_icons.py [path/to/minecraft-client.jar]
"""
import io
import math
import os
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFilter

SIZE = 512
FILL = (34, 36, 46)
BORDER = (64, 68, 84)
INSET, BORDER_W, RADIUS = 8, 6, 96
RED = (255, 58, 58)
GOLD = (255, 200, 70)
GOLD_S = (196, 132, 30)
OUTLINE = (14, 14, 20)

DEFAULT_JAR = os.path.expanduser("~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar")
HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, "icons")
TEX = "assets/minecraft/textures/entity/"


def load(jar, path):
    with jar.open(TEX + path) as f:
        return Image.open(io.BytesIO(f.read())).convert("RGBA")


def faces(skin, eyes=None):
    """Top, front, side 8x8 faces of a standard mob head (hat layer composited on top)."""
    def face(x, y):
        base = skin.crop((x, y, x + 8, y + 8))
        if skin.height >= 64:
            base.alpha_composite(skin.crop((x + 32, y, x + 40, y + 8)))
        if eyes is not None:
            base.alpha_composite(eyes.crop((x, y, x + 8, y + 8)))
        return base
    return face(8, 0), face(8, 8), face(16, 8)


def shade(c, k):
    return (round(c[0] * k), round(c[1] * k), round(c[2] * k), c[3])


def render_head(top, front, side, scale, bright=None):
    """Isometric head cube; returns a tight RGBA image."""
    c30 = math.cos(math.radians(30))
    w = int(16 * c30 * scale) + 4
    h = int(16 * scale) + 4
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    ox = 8 * c30 * scale + 2

    def proj(x, y, z):
        # x to screen right-down, z to screen left-down, y up
        return (x - z) * c30 * scale + ox, (x + z) * 0.5 * scale + (8 - y) * scale + 2

    def quad(pts, colour):
        if colour[3]:
            d.polygon([proj(*p) for p in pts], fill=colour[:3] + (255,))

    bright = bright or set()
    for j in range(8):
        for i in range(8):
            quad([(i, 8, j), (i + 1, 8, j), (i + 1, 8, j + 1), (i, 8, j + 1)], top.getpixel((i, j)))
    for j in range(8):
        for i in range(8):
            c = front.getpixel((i, j))
            k = 1.0 if c[:3] in bright else 0.84
            quad([(i, 8 - j, 8), (i + 1, 8 - j, 8), (i + 1, 7 - j, 8), (i, 7 - j, 8)], shade(c, k))
    for j in range(8):
        for i in range(8):
            c = side.getpixel((i, j))
            k = 1.0 if c[:3] in bright else 0.64
            quad([(8, 8 - j, 8 - i), (8, 8 - j, 7 - i), (8, 7 - j, 7 - i), (8, 7 - j, 8 - i)], shade(c, k))
    return img


def outlined(img, width=6):
    """Dark outline around the silhouette, like the family icons."""
    alpha = img.split()[3].point(lambda a: 255 if a > 0 else 0)
    grown = alpha.filter(ImageFilter.MaxFilter(width * 2 + 1))
    out = Image.new("RGBA", img.size, OUTLINE + (0,))
    out.putalpha(grown)
    out.alpha_composite(img)
    return out


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


def tile_mask():
    m = Image.new("L", (SIZE, SIZE), 0)
    i = INSET + BORDER_W
    ImageDraw.Draw(m).rounded_rectangle([i, i, SIZE - i - 1, SIZE - i - 1], radius=RADIUS - BORDER_W, fill=255)
    return m


def aura(icon, cx, cy, radius, colour, strength):
    """Soft glow behind the subject, kept inside the tile."""
    layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for r in range(radius, 0, -4):
        t = (1 - r / radius) ** 0.9 * strength
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=colour + (int(255 * t),))
    layer = layer.filter(ImageFilter.GaussianBlur(14))
    alpha = Image.composite(layer.split()[3], Image.new("L", (SIZE, SIZE), 0), tile_mask())
    layer.putalpha(alpha)
    icon.alpha_composite(layer)


def place(icon, head, cx, cy):
    head = outlined(head)
    icon.alpha_composite(head, (int(cx - head.width / 2), int(cy - head.height / 2)))


def crown(icon, cx, top_y, unit):
    rows = [
        "..#...#...#..",
        ".#y#.#y#.#y#.",
        ".#yy#yyy#yy#.",
        ".#yyyyyyyyy#.",
        ".#gygggggyg#.",
        ".###########.",
    ]
    colours = {"#": OUTLINE, "y": GOLD, "g": GOLD_S}
    d = ImageDraw.Draw(icon)
    x0 = cx - len(rows[0]) * unit / 2
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in colours:
                d.rectangle([x0 + i * unit, top_y + j * unit, x0 + (i + 1) * unit - 1, top_y + (j + 1) * unit - 1],
                            fill=colours[ch] + (255,))


def main():
    jar_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_JAR
    os.makedirs(OUT_DIR, exist_ok=True)

    with zipfile.ZipFile(jar_path) as jar:
        zombie = faces(load(jar, "zombie/zombie.png"))
        skeleton = faces(load(jar, "skeleton/skeleton.png"))
        wither = faces(load(jar, "skeleton/wither_skeleton.png"))
        creeper = faces(load(jar, "creeper/creeper.png"))
        ender_eyes = load(jar, "enderman/enderman_eyes.png")
        enderman = faces(load(jar, "enderman/enderman.png"), ender_eyes)
        eye_colours = {ender_eyes.getpixel((x, y))[:3] for x in range(64) for y in range(32)
                       if ender_eyes.getpixel((x, y))[3] > 0}

    drafts = {}

    icon = background()
    aura(icon, 256, 266, 230, RED, 0.85)
    place(icon, render_head(*wither, 22), 256, 266)
    drafts["e_wither_skull_red"] = icon

    icon = background()
    aura(icon, 256, 296, 220, (255, 170, 0), 0.75)
    place(icon, render_head(*zombie, 19), 256, 318)
    crown(icon, 256, 108, 15)
    drafts["f_crowned_zombie"] = icon

    icon = background()
    aura(icon, 256, 276, 240, RED, 0.7)
    place(icon, render_head(*creeper, 12), 152, 196)
    place(icon, render_head(*skeleton, 12), 360, 196)
    place(icon, render_head(*zombie, 16), 256, 318)
    drafts["g_three_heads"] = icon

    icon = background()
    aura(icon, 256, 266, 230, (170, 85, 255), 0.75)
    place(icon, render_head(*enderman, 22, eye_colours), 256, 266)
    drafts["h_enderman"] = icon

    for name, image in drafts.items():
        image.save(os.path.join(OUT_DIR, name + ".png"))

    tiles = list(drafts.values())
    sheet = Image.new("RGBA", (len(tiles) * 272 + 16, 288), (24, 24, 30, 255))
    for i, tile in enumerate(tiles):
        sheet.alpha_composite(tile.resize((256, 256), Image.LANCZOS), (16 + i * 272, 16))
    sheet.save(os.path.join(OUT_DIR, "sheet.png"))
    print("wrote", len(tiles), "drafts to", OUT_DIR)


if __name__ == "__main__":
    main()
