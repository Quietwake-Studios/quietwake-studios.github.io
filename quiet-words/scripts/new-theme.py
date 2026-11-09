#!/usr/bin/env python3
"""Create an empty theme for Quiet Words.

Usage:
  python3 scripts/new-theme.py <id> "<English name>" "<Nombre en español>" <sf.symbol>
  python3 scripts/new-theme.py coffee_tea "Coffee & Tea" "Café y té" cup.and.saucer

Writes Packages/QuietWordsCore/Sources/QuietWordsCore/Resources/Themes/<id>.json with empty
lists. Fill them (by hand or asking an agent: "write the Coffee & Tea theme"), then run
scripts/validate-words.py until it passes. The app loads every file in the folder: there is
nothing else to register.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
THEMES = os.path.join(HERE, '..', 'Packages', 'QuietWordsCore', 'Sources', 'QuietWordsCore', 'Resources', 'Themes')


def main():
    if len(sys.argv) != 5:
        sys.exit(__doc__)
    tid, en, es, symbol = sys.argv[1:]
    if not re.fullmatch(r'[a-z][a-z0-9_]*', tid):
        sys.exit('The id is lower case letters, digits and _ (it is the file name): coffee_tea')
    path = os.path.join(THEMES, tid + '.json')
    if os.path.exists(path):
        sys.exit(f'{path} already exists')
    empty = {'hidden': [], 'common': [], 'crafted': [], 'rare': [], 'regional': []}
    lines = ['{', f'  "symbol": {json.dumps(symbol)},']
    for i, (lang, name) in enumerate((('en', en), ('es', es))):
        lines.append(f'  "{lang}": {{')
        lines.append(f'    "name": {json.dumps(name, ensure_ascii=False)},')
        lines.append(',\n'.join(f'    "{k}": []' for k in empty))
        lines.append('  }' + (',' if i == 0 else ''))
    lines.append('}')
    open(path, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
    print(f'Created {os.path.relpath(path)}')
    print('Lists: hidden (15+ words of 4–9 letters), common (80+), crafted, rare, regional (up to 20); 200 words in all for the daily challenge.')


if __name__ == '__main__':
    main()
