#!/usr/bin/env python3
"""Validate the word themes of Quiet Words.

Usage:
  python3 scripts/validate-words.py            # every theme
  python3 scripts/validate-words.py ocean      # one theme

Each theme is one JSON file in Packages/QuietWordsCore/Sources/QuietWordsCore/Resources/Themes/
(the file name is the theme id). Messages say what to fix and where, e.g.
  ocean.json › es › rare: "Bentónico" repeated

Regional words (one country or region) go in "regional": at most 20 per theme and language.
Exit code 1 when a theme has errors. A theme short of words for the daily challenge is a
warning (it can still ship for free play with 120 words).
"""
import json
import os
import sys
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'Packages', 'QuietWordsCore', 'Sources', 'QuietWordsCore', 'Resources')
THEMES = os.path.join(RES, 'Themes')
BLOCK = os.path.join(RES, 'Blocklist')
GROUPS = ('common', 'crafted', 'rare', 'regional')
REGIONAL_MAX = 20
LANGS = ('en', 'es')
MIN_LEN, MAX_LEN = 3, 12          # 12×12 is the biggest board
HIDDEN_LEN = (4, 9)
DAILY_MIN, FREE_MIN, COMMON_MIN, HIDDEN_MIN = 200, 120, 80, 15


def norm(text, lang):
    """Board form: upper case, no accents or diaeresis (Ñ stays), no spaces or hyphens."""
    out = []
    for ch in text.upper():
        if ch == 'Ñ' and lang == 'es':
            out.append('Ñ')
            continue
        base = ''.join(c for c in unicodedata.normalize('NFD', ch) if unicodedata.category(c) != 'Mn')
        if base in (' ', '-', "'"):
            continue
        out.append(base)
    return ''.join(out)


def alphabet_ok(word, lang):
    allowed = set('ABCDEFGHIJKLMNOPQRSTUVWXYZ') | ({'Ñ'} if lang == 'es' else set())
    return word and all(c in allowed for c in word)


def load_blocklist(lang):
    path = os.path.join(BLOCK, f'{lang}.txt')
    words = []
    for line in open(path, encoding='utf-8'):
        line = line.split('#')[0].strip()
        if line:
            words.append(norm(line, lang))
    return words


def blocked(text, lang, block):
    """The word, or one of its parts ("Sea lion" → SEA, LION), is a blocked word. Blocked letters
    inside a real word (CALCULO, PEACOCK) are fine; on the board the generator also allows them
    only inside a placed word."""
    parts = {norm(text, lang)} | {norm(p, lang) for p in text.replace('-', ' ').split()}
    return next((b for b in block if b in parts), None)


def check(path, blocks):
    tid = os.path.splitext(os.path.basename(path))[0]
    name = os.path.basename(path)
    errors, warnings, report = [], [], []
    try:
        data = json.load(open(path, encoding='utf-8'))
    except json.JSONDecodeError as e:
        return [f'{name}: not valid JSON ({e})'], [], []
    if not data.get('symbol'):
        errors.append(f'{name}: missing "symbol" (an SF Symbol name, e.g. "water.waves")')
    langs = [l for l in LANGS if l in data]
    if not langs:
        errors.append(f'{name}: needs "en" and/or "es"')
    for lang in langs:
        part = data[lang]
        where = f'{name} › {lang}'
        if not part.get('name'):
            errors.append(f'{where}: missing "name"')
        seen = {}
        counts = {}
        lengths = {}
        for group in GROUPS:
            words = part.get(group, [])
            counts[group] = len(words)
            for w in words:
                n = norm(w, lang)
                at = f'{where} › {group}: "{w}"'
                if not alphabet_ok(n, lang):
                    errors.append(f'{at} has characters outside A–Z{" and Ñ" if lang == "es" else ""} ({n})')
                    continue
                if not MIN_LEN <= len(n) <= MAX_LEN:
                    errors.append(f'{at} has {len(n)} letters on the board ({n}); use {MIN_LEN}–{MAX_LEN}')
                if n in seen:
                    errors.append(f'{at} repeated (same as "{seen[n][1]}" in {seen[n][0]})')
                seen[n] = (group, w)
                hit = blocked(w, lang, blocks[lang])
                if hit:
                    errors.append(f'{at} contains a blocked word ({hit})')
                if w != w.strip() or '  ' in w:
                    errors.append(f'{at} has extra spaces')
                lengths[len(n)] = lengths.get(len(n), 0) + 1
        hidden = part.get('hidden', [])
        hidden_ok = 0
        for h in hidden:
            n = norm(h, lang)
            at = f'{where} › hidden: "{h}"'
            if not alphabet_ok(n, lang):
                errors.append(f'{at} has characters outside the alphabet')
            elif not HIDDEN_LEN[0] <= len(n) <= HIDDEN_LEN[1]:
                errors.append(f'{at} has {len(n)} letters; hidden words have {HIDDEN_LEN[0]}–{HIDDEN_LEN[1]}')
            else:
                hidden_ok += 1
            if n in seen:
                warnings.append(f'{at} is also a list word ({seen[n][0]}); boards with it as the hidden word skip that word')
            hit = blocked(h, lang, blocks[lang])
            if hit:
                errors.append(f'{at} contains a blocked word ({hit})')
        total = sum(counts.values())
        if total < FREE_MIN:
            errors.append(f'{where}: {total} words; free play needs {FREE_MIN}')
        elif total < DAILY_MIN:
            warnings.append(f'{where}: {total} words; the daily challenge needs {DAILY_MIN} (add {DAILY_MIN - total})')
        if counts['regional'] > REGIONAL_MAX:
            errors.append(f'{where} › regional: {counts["regional"]} words; at most {REGIONAL_MAX} per theme and language')
        if counts['common'] < COMMON_MIN:
            warnings.append(f'{where} › common: {counts["common"]} words; the daily challenge needs {COMMON_MIN}')
        if counts['crafted'] + counts['rare'] < 30:
            warnings.append(f'{where}: only {counts["crafted"] + counts["rare"]} crafted + rare words; Hard boards need variety')
        if hidden_ok < HIDDEN_MIN:
            warnings.append(f'{where} › hidden: {hidden_ok} usable; the daily challenge needs {HIDDEN_MIN}')
        for lo, hi in ((4, 6), (5, 7), (6, 9)):
            if sum(1 for h in hidden if lo <= len(norm(h, lang)) <= hi) < 3:
                warnings.append(f'{where} › hidden: fewer than 3 words of {lo}–{hi} letters')
        spread = ' '.join(f'{k}:{lengths[k]}' for k in sorted(lengths))
        report.append(f'{tid} {lang}: {total} words (common {counts["common"]}, crafted {counts["crafted"]}, rare {counts["rare"]}, regional {counts["regional"]}), '
                      f'{hidden_ok} hidden · lengths {spread}')
    if len(langs) == 1:
        warnings.append(f'{name}: only "{langs[0]}"; the theme joins the daily challenge with both languages')
    return errors, warnings, report


def check_lock():
    """Words already used by the daily can't move: the digest of each locked prefix must match."""
    path = os.path.join(THEMES, 'daily-lock.json')
    if not os.path.exists(path):
        return []
    import importlib.util
    spec = importlib.util.spec_from_file_location('lock', os.path.join(HERE, 'lock-daily.py'))
    lock_mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(lock_mod)
    errors = []
    for entry in json.load(open(path))['entries']:
        for tid, langs in entry['themes'].items():
            file = os.path.join(THEMES, tid + '.json')
            if not os.path.exists(file):
                errors.append(f'daily-lock.json: {tid}.json was deleted but the daily from {entry["from"]} uses it')
                continue
            data = json.load(open(file, encoding='utf-8'))
            for lang, counts in langs.items():
                for key in ('common', 'crafted', 'rare', 'regional', 'hidden'):
                    if key not in counts:
                        continue
                    words = data.get(lang, {}).get(key, [])[:counts[key]]
                    if len(words) < counts[key] or lock_mod.digest(words, lang) != counts.get(key + 'Digest'):
                        errors.append(f'{tid}.json › {lang} › {key}: the first {counts[key]} words are locked by the daily from '
                                      f'{entry["from"]}; add new words at the end instead of changing, moving or deleting them')
    return errors


def main():
    blocks = {lang: load_blocklist(lang) for lang in LANGS}
    names = sys.argv[1:]
    files = sorted(f for f in os.listdir(THEMES) if f.endswith('.json') and f != 'daily-lock.json')
    if names:
        files = [f for f in files if os.path.splitext(f)[0] in names]
    all_errors = 0
    for f in files:
        errors, warnings, report = check(os.path.join(THEMES, f), blocks)
        for line in report:
            print(line)
        for w in warnings:
            print('  warning:', w)
        for e in errors:
            print('  error:', e)
        all_errors += len(errors)
    for e in check_lock():
        print('  error:', e)
        all_errors += 1
    print(f'{len(files)} themes · {all_errors} errors')
    sys.exit(1 if all_errors else 0)


if __name__ == '__main__':
    main()
