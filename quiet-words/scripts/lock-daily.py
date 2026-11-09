#!/usr/bin/env python3
"""Freeze the daily challenge's words when a version ships.

Usage:
  python3 scripts/lock-daily.py 2026-12-01        # new entry from that day on
  python3 scripts/lock-daily.py --show

Appends an entry to Themes/daily-lock.json: from that day, which themes rotate (both
languages written) and how many words of each list they use. Days before keep their own
entry, so their boards never change. Rules for whoever writes themes:
  - new words go at the END of their list (common, crafted, rare, regional, hidden);
  - never delete or reorder words already locked (validate-words.py checks a digest);
  - fixing a bad word in the daily is an explicit exception: edit the digest by hand and say so
    in the commit.
The date should be the first day the new version can be on phones (after review).
"""
import json
import os
import sys
import importlib.util

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('vw', os.path.join(HERE, 'validate-words.py'))
vw = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vw)
LOCK = os.path.join(vw.THEMES, 'daily-lock.json')


def digest(words, lang):
    """FNV-1a 64 of the board forms joined by '|' (same as the validator)."""
    h = 0xCBF29CE484222325
    for byte in '|'.join(vw.norm(w, lang) for w in words).encode('utf-8'):
        h ^= byte
        h = (h * 0x100000001B3) & 0xFFFFFFFFFFFFFFFF
    return format(h, '016x')


def counts_for(part, lang):
    out = {}
    for key in ('common', 'crafted', 'rare', 'regional', 'hidden'):
        words = part.get(key, [])
        out[key] = len(words)
        out[key + 'Digest'] = digest(words, lang)
    return out


def main():
    lock = json.load(open(LOCK)) if os.path.exists(LOCK) else {'entries': []}
    if sys.argv[1:] == ['--show']:
        print(json.dumps(lock, indent=1, ensure_ascii=False))
        return
    if len(sys.argv) != 2 or len(sys.argv[1]) != 10:
        sys.exit(__doc__)
    day = sys.argv[1]
    if any(e['from'] >= day for e in lock['entries']):
        sys.exit(f'There is already an entry from {day} or later; days already locked never change.')
    themes = {}
    for f in sorted(os.listdir(vw.THEMES)):
        if not f.endswith('.json') or f == 'daily-lock.json':
            continue
        data = json.load(open(os.path.join(vw.THEMES, f), encoding='utf-8'))
        if not all(l in data for l in vw.LANGS):
            print(f'skip {f}: needs both languages for the daily')
            continue
        themes[os.path.splitext(f)[0]] = {l: counts_for(data[l], l) for l in vw.LANGS}
    lock['entries'].append({'from': day, 'themes': themes})
    json.dump(lock, open(LOCK, 'w'), indent=1, ensure_ascii=False)
    print(f'Locked {len(themes)} themes from {day} in {os.path.relpath(LOCK)}')


if __name__ == '__main__':
    main()
