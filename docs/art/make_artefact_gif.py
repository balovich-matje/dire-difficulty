#!/usr/bin/env python3
"""Artefact tooltip showcase GIF: one tooltip at a time, soft crossfade between them.

Input: tooltip screenshots in docs/art/media/tooltips/*.png (taken in game, hover in inventory).
Each is cropped to the tooltip box (drops the item slot at the left edge), centred on one
canvas, then held and crossfaded. Output: docs/art/media/artefacts.gif
Run: python3 docs/art/make_artefact_gif.py
"""
import glob
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "media", "tooltips")
OUT = os.path.join(HERE, "media", "artefacts.gif")
BG = (34, 36, 46)
SCALE = 2
PAD = 24
HOLD_MS = 1800
FADE_FRAMES = 8
FADE_MS = 50


def crop_tooltip(img):
    """The screenshots are already framed on the tooltip; this only drops the item slot that
    sticks in at the left edge: the first column that is mostly tooltip background starts it."""
    img = img.convert("RGB")
    w, h = img.size

    def tooltip_bg(p):
        return p[0] < 45 and p[1] < 25 and p[2] < 45 and abs(p[0] - p[2]) < 12

    for x in range(w):
        if sum(tooltip_bg(img.getpixel((x, y))) for y in range(h)) > h * 0.7:
            return img.crop((x, 0, w, h))
    return img


def main():
    tips = [crop_tooltip(Image.open(f)) for f in sorted(glob.glob(os.path.join(SRC, "*.png")))]
    tips = [t.resize((t.width * SCALE, t.height * SCALE), Image.NEAREST) for t in tips]
    cw = max(t.width for t in tips) + PAD * 2
    ch = max(t.height for t in tips) + PAD * 2

    def card(t):
        c = Image.new("RGB", (cw, ch), BG)
        c.paste(t, ((cw - t.width) // 2, (ch - t.height) // 2))
        return c

    cards = [card(t) for t in tips]
    frames, durations = [], []
    for i, c in enumerate(cards):
        frames.append(c)
        durations.append(HOLD_MS)
        nxt = cards[(i + 1) % len(cards)]
        blank = Image.new("RGB", (cw, ch), BG)
        half = FADE_FRAMES // 2
        for k in range(1, half + 1):
            frames.append(Image.blend(c, blank, k / half))
            durations.append(FADE_MS)
        for k in range(1, half):
            frames.append(Image.blend(blank, nxt, k / half))
            durations.append(FADE_MS)
    frames[0].save(OUT, save_all=True, append_images=frames[1:], duration=durations, loop=0, optimize=True)
    print("wrote", OUT, f"{cw}x{ch}", len(frames), "frames")


if __name__ == "__main__":
    main()
