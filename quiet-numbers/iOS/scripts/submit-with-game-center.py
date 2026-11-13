"""Attach a build to the editable version and submit it with every pending Game Center item.

    python3 quiet-numbers/iOS/scripts/submit-with-game-center.py <bundle suffix> <build number> [--apply]

Apple exige que los primeros ítems de Game Center de cada tipo viajen con una versión de la app
(STATE_ERROR.FIRST_OF_TYPE_MUST_ATTACH_VERSION_FOR_REVIEW), así que se envían siempre juntos.
"""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import asc  # noqa: E402

bundle, build_number = sys.argv[1], sys.argv[2]
apply = "--apply" in sys.argv
app = asc.request("GET", f"/v1/apps?filter[bundleId]=com.tavoohoh.{bundle}")["data"][0]["id"]

for _ in range(30):
    builds = asc.request("GET", f"/v1/builds?filter[app]={app}&filter[version]={build_number}")["data"]
    if builds and builds[0]["attributes"]["processingState"] != "PROCESSING":
        break
    time.sleep(30)
build = builds[0]
print("build", build_number, build["attributes"]["processingState"])

open_subs = [s for s in asc.request("GET", f"/v1/reviewSubmissions?filter[app]={app}&limit=10")["data"]
             if s["attributes"]["state"] in ("WAITING_FOR_REVIEW", "IN_REVIEW", "CANCELING", "READY_FOR_REVIEW")]
print("envíos abiertos", [s["attributes"]["state"] for s in open_subs])

version = next(v for v in asc.get_all(f"/v1/apps/{app}/appStoreVersions")
               if v["attributes"]["appStoreState"] not in ("READY_FOR_SALE", "REPLACED_WITH_NEW_VERSION"))
print("versión", version["attributes"]["versionString"], version["attributes"]["appStoreState"])

detail = asc.request("GET", f"/v1/apps/{app}/gameCenterDetail")["data"]["id"]
items = []
for kind, rel, typ in (("Leaderboards", "gameCenterLeaderboardVersion", "gameCenterLeaderboardVersions"),
                       ("Achievements", "gameCenterAchievementVersion", "gameCenterAchievementVersions")):
    for item in asc.get_all(f"/v1/gameCenterDetails/{detail}/gameCenter{kind}V2"):
        for v in asc.get_all(f"/v2/gameCenter{kind}/{item['id']}/versions"):
            if v["attributes"]["state"] == "PREPARE_FOR_SUBMISSION":
                items.append((rel, typ, v["id"], item["attributes"]["vendorIdentifier"]))
print(len(items), "ítems de Game Center:", [i[3] for i in items])

if not apply:
    raise SystemExit("Dry run; pass --apply.")
if any(s["attributes"]["state"] in ("WAITING_FOR_REVIEW", "IN_REVIEW", "CANCELING") for s in open_subs):
    raise SystemExit("Todavía hay un envío en revisión o cancelándose.")

vid = version["id"]
asc.request("PATCH", f"/v1/appStoreVersions/{vid}/relationships/build", {"data": {"type": "builds", "id": build["id"]}})
if not asc.request("GET", f"/v1/appStoreVersions/{vid}/gameCenterAppVersion").get("data"):
    asc.request("POST", "/v1/gameCenterAppVersions", {"data": {"type": "gameCenterAppVersions", "relationships": {
        "appStoreVersion": {"data": {"type": "appStoreVersions", "id": vid}}}}})

draft = next((s for s in open_subs if s["attributes"]["state"] == "READY_FOR_REVIEW"), None)
sub = draft["id"] if draft else asc.request("POST", "/v1/reviewSubmissions", {"data": {
    "type": "reviewSubmissions", "attributes": {"platform": "IOS"},
    "relationships": {"app": {"data": {"type": "apps", "id": app}}}}})["data"]["id"]


def add(rel, typ, item_id):
    asc.request("POST", "/v1/reviewSubmissionItems", {"data": {"type": "reviewSubmissionItems", "relationships": {
        "reviewSubmission": {"data": {"type": "reviewSubmissions", "id": sub}},
        rel: {"data": {"type": typ, "id": item_id}}}}})


present = asc.get_all(f"/v1/reviewSubmissions/{sub}/items")
if not present:
    add("appStoreVersion", "appStoreVersions", vid)
    for rel, typ, item_id, _ in items:
        add(rel, typ, item_id)
asc.request("PATCH", f"/v1/reviewSubmissions/{sub}", {"data": {
    "type": "reviewSubmissions", "id": sub, "attributes": {"submitted": True}}})
state = asc.request("GET", f"/v1/reviewSubmissions/{sub}")["data"]["attributes"]
print("envío", state["state"], state.get("submittedDate"), "ítems", len(asc.get_all(f"/v1/reviewSubmissions/{sub}/items")))
