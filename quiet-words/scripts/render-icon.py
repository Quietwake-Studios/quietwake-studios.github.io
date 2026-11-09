#!/usr/bin/env python3
"""Renders the app icons (standard and Gold; light, dark and tinted) and the Home icon.

    scripts/render-icon.py

A 3×3 grid of letters with the selection stroke over the diagonal (S-O-L), in coral #FF9277
on the vivid teal gradient #19BFCD → #0A8AA5 (palette A2, like the other Quiet icons). From
the design canvas; the letters are an open decision until the store listing (2026-11-08).
SF Pro Rounded Heavy from the system fonts; drawn at 2× and scaled down.
"""
import json
import math
import pathlib

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "App/Resources/Assets.xcassets"
FONT = "/System/Library/Fonts/SFNSRounded.ttf"
LETTERS = ["S", "A", "N", "R", "O", "E", "M", "I", "L"]

# name → (background top, bottom (None = transparent), letters, stroke, letters on the stroke)
VARIANTS = {
    "light": ("#19BFCD", "#0A8AA5", "#FFFFFF", "#FF9277", "#FFFFFF"),
    "dark": ("#0B2226", "#03171B", "#2FD6E4", "#FF9277", "#03171B"),
    "tinted": (None, None, "#8C8C8C", "#FFFFFF", "#000000"),
}
GOLD = {
    "light": ("#E3B043", "#B27A17", "#FFFFFF", "#FFF1CF", "#9A6A12"),
    "dark": ("#1A1408", "#0F0B04", "#F0BC4E", "#FF9277", "#1A1408"),
    "tinted": VARIANTS["tinted"],
}


def rgb(hex_):
    return tuple(int(hex_[i:i + 2], 16) for i in (1, 3, 5))


def render(top, bottom, ink, stroke, on_stroke, size=1024):
    S = size * 2
    if top:
        bg = Image.new("RGB", (S, S))
        a, b = rgb(top), rgb(bottom)
        draw = ImageDraw.Draw(bg)
        for y in range(S):
            t = y / (S - 1)
            draw.line([(0, y), (S, y)], fill=tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)))
        image = bg.convert("RGBA")
    else:
        image = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    cell = S * 0.24
    off = (S - cell * 3) / 2
    # The stroke: a capsule from the first to the last diagonal cell.
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    h = cell * 0.84
    length = math.hypot(2 * cell, 2 * cell) + h
    capsule = Image.new("RGBA", (int(length), int(h)), (0, 0, 0, 0))
    ImageDraw.Draw(capsule).rounded_rectangle([0, 0, length - 1, h - 1], radius=h / 2, fill=rgb(stroke) + (255,))
    capsule = capsule.rotate(-45, expand=True, resample=Image.BICUBIC)
    center = off + 1.5 * cell
    layer.paste(capsule, (int(center - capsule.width / 2), int(center - capsule.height / 2)), capsule)
    image = Image.alpha_composite(image, layer)
    font = ImageFont.truetype(FONT, int(cell * 0.62))
    try:
        font.set_variation_by_name("Heavy")
    except Exception:
        pass
    draw = ImageDraw.Draw(image)
    for i, ch in enumerate(LETTERS):
        r, c = divmod(i, 3)
        x = off + c * cell + cell / 2
        y = off + r * cell + cell / 2
        draw.text((x, y), ch, font=font, fill=rgb(on_stroke if r == c else ink) + (255,), anchor="mm")
    image = image.resize((size, size), Image.LANCZOS)
    return image if not top else image.convert("RGB")


def write_set(name, variants):
    folder = ASSETS / f"{name}.appiconset"
    folder.mkdir(parents=True, exist_ok=True)
    files = {"light": "icon-1024.png", "dark": "icon-1024-dark.png", "tinted": "icon-1024-tinted.png"}
    for key, file in files.items():
        render(*variants[key]).save(folder / file)
    images = [{"filename": files["light"], "idiom": "universal", "platform": "ios", "size": "1024x1024"}]
    for key in ("dark", "tinted"):
        images.append({"filename": files[key], "idiom": "universal", "platform": "ios", "size": "1024x1024",
                       "appearances": [{"appearance": "luminosity", "value": key}]})
    (folder / "Contents.json").write_text(json.dumps({"images": images, "info": {"author": "xcode", "version": 1}}, indent=2) + "\n")


def main():
    write_set("AppIcon", VARIANTS)
    write_set("AppIcon-Gold", GOLD)
    home = ASSETS / "HomeIcon.imageset"
    home.mkdir(parents=True, exist_ok=True)
    render(*VARIANTS["light"], size=256).save(home / "icon.png")
    (home / "Contents.json").write_text(json.dumps({"images": [{"filename": "icon.png", "idiom": "universal"}],
                                                    "info": {"author": "xcode", "version": 1}}, indent=2) + "\n")
    print("Icons written to", ASSETS)


if __name__ == "__main__":
    main()
