#!/usr/bin/env python3
"""Creates Quiet Words' Play Games achievements and leaderboards through the
Google Play Games Services Publishing API, and writes their IDs to games-ids.xml.

    python3 scripts/setup-play-games.py <play games project id>            # dry run: shows what it would do
    python3 scripts/setup-play-games.py <play games project id> --apply    # creates what's missing

Source of truth: ../iOS/AppStore/game-center.json (same concepts, names and points as Game
Center) plus the Play-only details below (incremental steps). Icons: PlayConsole/achievements/.
Idempotent: an achievement or leaderboard whose English name already exists is reused, never
duplicated. Credentials: the fastlane service account (~/.config/quietwake/play-service-account.json
or PLAY_JSON_KEY); its Cloud project needs "Google Play Games Services Publishing API" enabled.
Nothing is published: the game project stays in draft until the user publishes it in Play Console.
Needs google-auth (pip3 install google-auth requests).
"""
import json
import os
import pathlib
import sys

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

ROOT = pathlib.Path(__file__).resolve().parent.parent
GC = json.loads((ROOT.parent / "iOS/AppStore/game-center.json").read_text())
ICONS = ROOT / "PlayConsole/achievements"
IDS = ROOT / "words-app/src/main/res/values/games-ids.xml"
API = "https://gamesconfiguration.googleapis.com/games/v1configuration"
UPLOAD = "https://gamesconfiguration.googleapis.com/upload/games/v1configuration"
LOCALES = {"en": "en-US", "es": os.environ.get("PGS_ES_LOCALE", "es-419")}

# Play-only: counted achievements are incremental with the iOS target as steps.
STEPS = {"qw_words_100": 100, "qw_words_1000": 1000, "qw_words_10000": 10000,
         "qw_daily_7": 7, "qw_daily_30": 30}


def bundle(texts):
    return {"kind": "gamesConfiguration#localizedStringBundle",
            "translations": [{"kind": "gamesConfiguration#localizedString",
                              "locale": LOCALES[lang], "value": value}
                             for lang, value in texts.items()]}


def english(detail):
    for t in (detail.get("name") or {}).get("translations", []):
        if t.get("locale", "").startswith("en"):
            return t.get("value")


def session():
    key = os.environ.get("PLAY_JSON_KEY") or os.path.expanduser("~/.config/quietwake/play-service-account.json")
    cred = service_account.Credentials.from_service_account_file(
        key, scopes=["https://www.googleapis.com/auth/androidpublisher"])
    return AuthorizedSession(cred)


def call(s, method, url, **kw):
    import time
    for wait in (2, 5, 10, 20, 0):
        r = s.request(method, url, **kw)
        if r.status_code < 500 or not wait:
            break
        time.sleep(wait)  # 5xx (seen on icon uploads): retry
    if r.status_code >= 300:
        sys.exit(f"{method} {url} → {r.status_code}\n{r.text[:800]}")
    return r.json() if r.text else {}


def listing(s, app, kind):
    items, token = [], None
    while True:
        page = call(s, "GET", f"{API}/applications/{app}/{kind}", params={"pageToken": token} if token else None)
        items += page.get("items", [])
        token = page.get("nextPageToken")
        if not token:
            return items


ICONS_MISSING = []


def upload_icon(s, resource_id, image_type, path):
    """The image upload endpoint answered 503 on every try (2026-11-18, media and multipart),
    so a failed icon is reported and left for Play Console instead of stopping the run."""
    import time
    for wait in (2, 5, 0):
        r = s.post(f"{UPLOAD}/images/{resource_id}/imageType/{image_type}",
                   params={"uploadType": "media"}, data=path.read_bytes(), headers={"Content-Type": "image/png"})
        if r.status_code < 300:
            return
        if wait:
            time.sleep(wait)
    ICONS_MISSING.append(path.name)
    print(f"  ! icon {path.name} not uploaded ({r.status_code}); upload it in Play Console")


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    apply = "--apply" in sys.argv
    if len(args) != 1 or not args[0].isdigit():
        sys.exit(__doc__)
    app = args[0]
    s = session()
    ids = {"play_games_app_id": app}

    existing = {english(a.get("draft") or a.get("published") or {}): a for a in listing(s, app, "achievements")}
    for rank, a in enumerate(GC["achievements"], start=1):
        name = a["name"]["en"]
        if name in existing:
            found = existing[name]
            ids[f"achievement_{a['id']}"] = found["id"]
            has_icon = (found.get("draft") or {}).get("iconUrl")
            print(f"= achievement {a['id']} ({name}) already exists: {found['id']}" + ("" if has_icon else ", icon missing"))
            if apply and not has_icon:
                upload_icon(s, found["id"], "ACHIEVEMENT_ICON", ICONS / f"{a['id']}.png")
            continue
        steps = STEPS.get(a["id"])
        body = {
            "kind": "gamesConfiguration#achievementConfiguration",
            "achievementType": "INCREMENTAL" if steps else "STANDARD",
            "initialState": "HIDDEN" if a["hidden"] else "REVEALED",
            "draft": {
                "kind": "gamesConfiguration#achievementConfigurationDetail",
                "name": bundle(a["name"]),
                "description": bundle(a["preEarnedDescription"]),
                "pointValue": a["points"],
                "sortRank": rank,
            },
        }
        if steps:
            body["stepsToUnlock"] = steps
        icon = ICONS / f"{a['id']}.png"
        print(f"+ achievement {a['id']} ({name}, {a['points']} pts, "
              f"{'incremental ' + str(steps) if steps else 'standard'}, {body['initialState'].lower()}, icon {icon.name})")
        if apply:
            created = call(s, "POST", f"{API}/applications/{app}/achievements", json=body)
            upload_icon(s, created["id"], "ACHIEVEMENT_ICON", icon)
            ids[f"achievement_{a['id']}"] = created["id"]

    existing = {english(l.get("draft") or l.get("published") or {}): l for l in listing(s, app, "leaderboards")}
    for rank, l in enumerate(GC["leaderboards"], start=1):
        name = l["name"]["en"]
        if name in existing:
            ids[f"leaderboard_{l['id']}"] = existing[name]["id"]
            print(f"= leaderboard {l['id']} ({name}) already exists: {existing[name]['id']}")
            continue
        suffix = {lang: text.strip() for lang, text in l.get("formatSuffix", {}).items()}
        score_format = {"numberFormatType": "NUMERIC", "numDecimalPlaces": 0}
        if suffix:
            # Play wants every plural form; the singular drops the final s ("1 point", "1 punto", "1 day", "1 día").
            many = bundle({k: " " + v for k, v in suffix.items()})
            one = bundle({k: " " + (v[:-1] if v.endswith("s") else v) for k, v in suffix.items()})
            score_format["suffix"] = {"zero": many, "one": one, "two": many, "few": many, "many": many, "other": many}
        body = {
            "kind": "gamesConfiguration#leaderboardConfiguration",
            "scoreOrder": "LARGER_IS_BETTER" if l["sortOrder"] == "High to Low" else "SMALLER_IS_BETTER",
            "draft": {
                "kind": "gamesConfiguration#leaderboardConfigurationDetail",
                "name": bundle(l["name"]),
                "sortRank": rank,
                "scoreFormat": score_format,
            },
        }
        print(f"+ leaderboard {l['id']} ({name}, {body['scoreOrder'].lower()}, suffix {suffix or '-'})")
        if apply:
            created = call(s, "POST", f"{API}/applications/{app}/leaderboards", json=body)
            ids[f"leaderboard_{l['id']}"] = created["id"]

    if not apply:
        print("\nDry run: nothing created. Run again with --apply.")
        return
    text = IDS.read_text()
    for key, value in ids.items():
        start = text.index(f'name="{key}"')
        open_end = text.index(">", start) + 1
        close = text.index("</string>", open_end)
        text = text[:open_end] + value + text[close:]
    IDS.write_text(text)
    if ICONS_MISSING:
        print(f"Icons to upload by hand in Play Console (PlayConsole/achievements/): {', '.join(ICONS_MISSING)}")
    missing = [k for k in ids if not ids[k]]
    print(f"\nWrote {len(ids)} IDs to {IDS.relative_to(ROOT)}" + (f"; missing {missing}" if missing else ""))


if __name__ == "__main__":
    main()
