#!/usr/bin/env python3
"""Google Play store images for Quiet Words (Android), from the iOS store art:

    scripts/screenshots.sh <serial> phone      # real captures first (QW_Phone, then QW_Tablet)
    scripts/screenshots.sh <serial> tablet
    python3 scripts/store-images.py            # → PlayConsole/store/

- icon-512.png: 512×512, 32-bit PNG, the standard launcher art full-bleed (Play applies its mask).
- feature-graphic.<en|es>.png: 1024×500, 24-bit PNG, the iOS header (iOS/AppStore/creative/
  header.<lang>.html) rendered at Play's ratio with its content centred.
- phone/<en|es>/NN-name.png: 1080×1920 (9:16), the five stories of iOS/scripts/store-images.py
  (same titles, adapted to Android) around a real capture.
- tablet/<en|es>/NN-name.png: 2560×1600, four of them for 10-inch tablets.

Rendered with Chrome headless like the iOS images; the type is Nunito (Google Fonts, the iOS
header's fallback) instead of SF Pro Rounded, whose license is for Apple platforms. Colors and
sizes come from iOS/AppStore/store-style.json. No alpha except the icon (Play rejects it).
"""
import importlib.util
import json
import pathlib
import re
import subprocess
import tempfile

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent                      # quiet-words/Android
IOS = ROOT.parent / "iOS"
SHOTS = ROOT / "PlayConsole/screenshots"
OUT = ROOT / "PlayConsole/store"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
STYLE = json.loads((IOS / "AppStore/store-style.json").read_text())
FONT = '<link href="https://fonts.googleapis.com/css2?family=Nunito:wght@700;800;900&display=swap" rel="stylesheet">'

# The iOS stories (iOS/scripts/store-images.py), titles in two lines (white, then peach).
# Nothing there names Game Center, iCloud or the dictionary, so they stay as they are.
TEXT = {
    "en": {
        1: ("Themed<br><span>word search.</span>", "Ocean, space, coffee<br>and more."),
        2: ("A new challenge<br><span>every day.</span>", "In English and in Spanish."),
        3: ("Play with<br><span>your friends.</span>", "Same words,<br>see who's faster."),
        4: ("Earned<br><span>by playing.</span>", "An album of every word,<br>themes and more."),
        5: ("A hidden word<br><span>in every puzzle.</span>", "Made of the letters left over."),
    },
    "es": {
        1: ("Sopa de letras<br><span>con temas.</span>", "Océano, espacio, café<br>y más."),
        2: ("Un reto nuevo<br><span>cada día.</span>", "En español y en inglés."),
        3: ("Juega con<br><span>tus amigos.</span>", "Las mismas palabras, a ver<br>quién va más rápido."),
        4: ("Todo se gana<br><span>jugando.</span>", "Un álbum con cada palabra,<br>temas y más."),
        5: ("Una palabra oculta<br><span>en cada sopa.</span>", "Hecha con las letras que sobran."),
    },
}
STORIES = {1: ("01-board", "01-game"), 2: ("02-daily", "02-daily"), 3: ("03-friends", "03-leaderboards"),
           4: ("04-album", "04-album"), 5: ("05-hidden", "05-hidden")}
TABLET_STORIES = [1, 2, 4, 5]


def background(name):
    bg = STYLE["pieces"].get(name, {}).get("background", STYLE["background"])
    return f"linear-gradient(180deg, {bg['top']}, {bg['bottom']})"


def render(html, size, scale, tmp, out, alpha=False):
    src = pathlib.Path(tmp) / "page.html"
    src.write_text(html)
    raw = pathlib.Path(tmp) / "raw.png"
    w, h = size
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--allow-file-access-from-files",
                    f"--force-device-scale-factor={scale}", f"--window-size={w},{h}", "--virtual-time-budget=5000",
                    f"--screenshot={raw}", src.as_uri()], check=True, capture_output=True)
    out.parent.mkdir(parents=True, exist_ok=True)
    image = Image.open(raw)
    (image.convert("RGBA") if alpha else image.convert("RGB")).save(out)
    print(out.relative_to(ROOT))


def icon():
    spec = importlib.util.spec_from_file_location("ios_icon", IOS / "scripts/render-icon.py")
    ios = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(ios)
    out = OUT / "icon-512.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    ios.render(*ios.VARIANTS["light"], size=512).convert("RGBA").save(out)
    print(out.relative_to(ROOT))


def feature_graphic(language, tmp):
    """The iOS header (1280×549) at Play's 1024×500 ratio: same design, laid out on 1280×625."""
    html = (IOS / f"AppStore/creative/header.{language}.html").read_text()
    html = html.replace("width:1280px;height:549px", "width:1280px;height:625px")
    html = html.replace("ui-rounded, 'SF Pro Rounded', Nunito", "Nunito")
    html = re.sub(r"var\(--bg, ([^)]*\)[^)]*)\)", lambda m: m.group(1), html)
    raw = OUT / f"feature-graphic.{language}.raw.png"
    render(html, (1280, 625), 1, tmp, raw)
    Image.open(raw).resize((1024, 500), Image.LANCZOS).convert("RGB").save(OUT / f"feature-graphic.{language}.png")
    raw.unlink()
    print((OUT / f"feature-graphic.{language}.png").relative_to(ROOT))


CSS = """
* { box-sizing: border-box; } html, body { margin: 0; }
body { width: %(w)spx; height: %(h)spx; position: relative; overflow: hidden; background: %(bg)s; color: #FFFFFF;
       font-family: Nunito, system-ui, sans-serif; }
h1 { margin: 0; font-size: %(h1)spx; line-height: 1.04; font-weight: 900; letter-spacing: -0.01em; white-space: nowrap; color: #FFFFFF; }
h1 span { color: %(accent)s; }
p { margin: 0; font-size: %(sub)spx; line-height: 1.3; font-weight: 800; white-space: nowrap; color: rgba(255,255,255,0.92); }
header { position: absolute; display: flex; flex-direction: column; gap: %(gap)spx; }
.device { position: absolute; padding: %(bezel)spx; border-radius: %(radius)spx; background: %(bezel_color)s;
          box-shadow: 0 30px 70px rgba(2,30,48,0.42); }
.device img { display: block; width: 100%%; border-radius: %(inner)spx; }
"""

FIT = """<script>
const fit = () => { for (const e of document.querySelectorAll('h1, p')) {
  e.style.fontSize = ''; const max = e.parentElement.clientWidth; let s = parseFloat(getComputedStyle(e).fontSize);
  while (e.scrollWidth > max && s > 12) { s -= 1; e.style.fontSize = s + 'px'; } } };
document.fonts.ready.then(fit); fit();
</script>"""


def phone_page(language, number, shot, dark):
    """360×640 CSS px at 3× = 1080×1920: the title on top, the phone below running off the bottom (as on iOS)."""
    title, subtitle = TEXT[language][number]
    css = CSS % dict(w=360, h=640, bg=background(STORIES[number][0]), accent=STYLE["title"]["accent"], h1=36, sub=16, gap=8,
                     bezel=6, radius=36, inner=30, bezel_color="#000000" if dark else "#0A1416")
    return f"""<!doctype html><html lang="{language}"><head><meta charset="utf-8">{FONT}<style>{css}
header {{ left: 28px; right: 28px; top: 40px; }}
.device {{ left: 50px; width: 260px; top: 196px; }}
</style></head><body><header><h1>{title}</h1><p>{subtitle}</p></header>
<div class="device"><img src="{shot.as_uri()}"></div>{FIT}</body></html>"""


def tablet_page(language, number, shot, dark):
    """1280×800 CSS px at 2× = 2560×1600: the title on the left, the tablet on the right."""
    title, subtitle = TEXT[language][number]
    css = CSS % dict(w=1280, h=800, bg=background(STORIES[number][0]), accent=STYLE["title"]["accent"], h1=58, sub=24, gap=14,
                     bezel=12, radius=34, inner=22, bezel_color="#000000" if dark else "#0A1416")
    return f"""<!doctype html><html lang="{language}"><head><meta charset="utf-8">{FONT}<style>{css}
header {{ left: 64px; width: 380px; top: 50%; transform: translateY(-50%); }}
.device {{ left: 470px; width: 780px; top: 50%; transform: translateY(-50%); }}
</style></head><body><header><h1>{title}</h1><p>{subtitle}</p></header>
<div class="device"><img src="{shot.as_uri()}"></div>{FIT}</body></html>"""


DARK = {3, 4}   # Like iOS: friends and the album in dark mode.


def main():
    with tempfile.TemporaryDirectory() as tmp:
        icon()
        for language in TEXT:
            feature_graphic(language, tmp)
            for number, (name, shot_name) in STORIES.items():
                shot = SHOTS / "phone" / language / f"{shot_name}.png"
                if shot.exists():
                    render(phone_page(language, number, shot, number in DARK), (360, 640), 3, tmp, OUT / "phone" / language / f"{name}.png")
                else:
                    print(f"Missing {shot.relative_to(ROOT)}; run scripts/screenshots.sh <serial> phone first")
                if number in TABLET_STORIES:
                    shot = SHOTS / "tablet" / language / f"{shot_name}.png"
                    if shot.exists():
                        render(tablet_page(language, number, shot, number in DARK), (1280, 800), 2, tmp, OUT / "tablet" / language / f"{name}.png")
                    else:
                        print(f"Missing {shot.relative_to(ROOT)}; run scripts/screenshots.sh <serial> tablet first")


if __name__ == "__main__":
    main()
