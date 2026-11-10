#!/usr/bin/env python3
"""Renders the 10 Game Center badges to AppStore/achievements/<id>.png (1024×1024, no alpha).

    python3 scripts/achievement-badges.py

Same system as the other Quiet games (a colored square Game Center crops round, a faint ring,
a white line motif), drawn with AppKit through scripts/render-svg.swift. The IDs come from
AppStore/game-center.json; the script fails if one has no badge.
"""
import json
import pathlib
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "AppStore" / "achievements"

TEAL, NIGHT, GOLD, CORAL = "#0E6B78", "#0F1A1D", "#A8741A", "#B9472F"
WHITE, NIGHT_INK = "#FFFFFF", "#5BC2CF"


def circle(cx, cy, r):
    return f"M{cx - r} {cy} a{r} {r} 0 1 0 {2 * r} 0 a{r} {r} 0 1 0 {-2 * r} 0"


def line(d, ink, width=4.5, dash=None):
    dash = f' stroke-dasharray="{dash}"' if dash else ""
    return (f'<path d="{d}" fill="none" stroke="{ink}" stroke-width="{width}"{dash} '
            'stroke-linecap="round" stroke-linejoin="round"/>')


def dot(d, ink):
    return f'<path d="{d}" fill="{ink}"/>'


def letters(ink, stroke_on=None, size=42, x=29, y=29):
    """A 3×3 grid of letter dots, with the selection stroke over the diagonal (the app icon)."""
    step = size / 2
    parts = []
    if stroke_on:
        parts.append(line(f"M{x} {y} L{x + size} {y + size}", stroke_on, 13))
    for r in range(3):
        for c in range(3):
            parts.append(dot(circle(x + c * step, y + r * step, 3.2), ink))
    return parts


def capsules(ink, rows, x=30, width=40, y0=34, gap=11):
    """Found words: rounded strokes stacked like a word list."""
    return [line(f"M{x} {y0 + i * gap} H{x + width - (i % 2) * 10}", ink, 6) for i in range(rows)]


def calendar(ink):
    return [
        line("M34 32 H66 a4 4 0 0 1 4 4 V68 a4 4 0 0 1 -4 4 H34 a4 4 0 0 1 -4 -4 V36 a4 4 0 0 1 4 -4 Z", ink),
        line("M30 44 H70", ink, 3.5),
        line("M41 27 V36 M59 27 V36", ink),
    ]


ROSETTE = lambda ink: [line(circle(50, 42, 14), ink), dot(circle(50, 42, 5), ink),
                       line("M43 55 L37 75 L45 70 L50 77 L55 70 L63 75 L57 55", ink)]

BADGES = {
    # The board with the first word found (the icon's stroke).
    "qw_first_puzzle": (TEAL, WHITE, letters(WHITE, stroke_on="#FF9277")),
    # A short list of found words.
    "qw_words_100": (TEAL, WHITE, capsules(WHITE, 3, y0=39)),
    # A longer list, in gold (the gold icon).
    "qw_words_1000": (GOLD, WHITE, capsules(WHITE, 4, y0=33)),
    # An open book: ten thousand words.
    "qw_words_10000": (NIGHT, NIGHT_INK, [line("M50 36 C42 30 32 30 26 33 V68 C32 65 42 65 50 71 C58 65 68 65 74 68 V33 C68 30 58 30 50 36 Z", NIGHT_INK),
                                           line("M50 36 V71", NIGHT_INK, 3.5)]),
    # The theme's badge.
    "qw_theme_complete": (TEAL, WHITE, ROSETTE(WHITE)),
    # An eye: the hidden word seen early.
    "qw_hidden_early": (TEAL, WHITE, [line("M24 50 C32 37 41 32 50 32 C59 32 68 37 76 50 C68 63 59 68 50 68 C41 68 32 63 24 50 Z", WHITE),
                                       line(circle(50, 50, 8), WHITE)]),
    # Seven days marked.
    "qw_daily_7": (TEAL, WHITE, calendar(WHITE) + [dot(" ".join(
        [circle(x, 54, 2.8) for x in (38, 46, 54, 62)] + [circle(x, 63, 2.8) for x in (38, 46, 54)]), WHITE)]),
    # A month: the calendar with a check.
    "qw_daily_30": (NIGHT, NIGHT_INK, calendar(NIGHT_INK) + [line("M40 57 L47 64 L61 50", NIGHT_INK)]),
    # A clock under five minutes.
    "qw_hard_fast": (TEAL, WHITE, [line(circle(50, 50, 22), WHITE), line("M50 36 V50 L60 56", WHITE)]),
    # Two speech bubbles: both languages.
    "qw_bilingual": (CORAL, WHITE, [line("M28 32 H52 a4 4 0 0 1 4 4 V50 a4 4 0 0 1 -4 4 H40 L32 61 V54 H28 a4 4 0 0 1 -4 -4 V36 a4 4 0 0 1 4 -4 Z", WHITE),
                                     line("M62 44 H72 a4 4 0 0 1 4 4 V62 a4 4 0 0 1 -4 4 H70 V73 L62 66 H48 a4 4 0 0 1 -4 -4 V58", WHITE)]),
    # The score line dropping under the dashed zero, and a minus: "In the Red" (hidden).
    "qw_in_the_red": (CORAL, WHITE, [line("M22 44 H78", WHITE, 3, dash="4 5"),
                                      line("M24 32 L40 38 L52 52 L72 66", WHITE),
                                      line("M64 66 H72 V58", WHITE),
                                      line("M30 66 H42", WHITE, 5)]),
}


def badge(background, ink, paths):
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100">'
            f'<rect width="100" height="100" fill="{background}"/>'
            f'<circle cx="50" cy="50" r="41" fill="none" stroke="{ink}" stroke-opacity="0.3" stroke-width="1.5"/>'
            + "".join(paths) + "</svg>")


def main():
    ids = [a["id"] for a in json.loads((ROOT / "AppStore/game-center.json").read_text())["achievements"]]
    missing = sorted(set(ids) - set(BADGES))
    if missing:
        sys.exit(f"Badges missing for {missing}")
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for identifier in ids:
            background, ink, paths = BADGES[identifier]
            source = pathlib.Path(tmp) / f"{identifier}.svg"
            source.write_text(badge(background, ink, paths))
            subprocess.run(["swift", str(ROOT / "scripts/render-svg.swift"), str(source), str(OUT / f"{identifier}.png"),
                            "1024", "1024", "opaque"], check=True)
    print(f"{len(ids)} badges in {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
