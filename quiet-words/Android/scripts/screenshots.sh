#!/bin/bash
# Real screenshots for Google Play, in the debug demo state (like ../iOS/scripts/screenshots.sh):
#
#   scripts/screenshots.sh <serial> phone|tablet      (from quiet-words/Android, one emulator running)
#
# → PlayConsole/screenshots/<phone|tablet>/<en|es>/NN-name.png, then scripts/store-images.py.
# Installs the debug build, puts the status bar in demo mode (09:41, full battery, Wi-Fi, no
# notifications) and opens each scene with debug launch arguments. QW_Phone / QW_Tablet only.
set -euo pipefail
SERIAL=${1:?serial, e.g. emulator-5580}; DEVICE=${2:?phone or tablet}
cd "$(dirname "$0")/.."
ADB="$HOME/Library/Android/sdk/platform-tools/adb -s $SERIAL"
PKG=com.tavoohoh.quietwords
WAIT=${WAIT:-12}
export JAVA_HOME=${JAVA_HOME:-$HOME/Library/Java/JavaVirtualMachines/jbr-17.0.12/Contents/Home}
ANDROID_SERIAL=$SERIAL ./gradlew -q :words-app:installDebug

demo() { $ADB shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
$ADB shell settings put global sysui_demo_allowed 1
demo enter
demo clock -e hhmm 0941
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile hide
demo notifications -e visible false

# name | appearance | extras (the same scenes as iOS's screenshots.sh)
SCENES=(
  "01-game|light|--ez startGame true --es seedKey quiet-store --es level medium --es theme ocean --ei found 7 --ei clock 192"
  "02-daily|light|--es open daily"
  "03-leaderboards|dark|--es open leaderboards"
  "04-album|dark|--es albumTheme ocean"
  "05-hidden|light|--ez startGame true --es seedKey quiet-win --es level medium --es theme space --ez nearlyWon true --ez won true --ei clock 248"
)
for LANG_CODE in en es; do
  OUT=PlayConsole/screenshots/$DEVICE/$LANG_CODE
  mkdir -p "$OUT"
  $ADB shell cmd locale set-app-locales $PKG --locales $LANG_CODE >/dev/null 2>&1 || true
  for entry in "${SCENES[@]}"; do
    IFS='|' read -r name appearance extras <<< "$entry"
    # The system's mode too, so the status bar icons contrast with the screen.
    $ADB shell cmd uimode night "$([ "$appearance" = dark ] && echo yes || echo no)" >/dev/null
    # shellcheck disable=SC2086
    $ADB shell am start -S -W -n $PKG/.MainActivity --ez uiTesting true --ez tutorialDone true --ez demo true \
      --es words $LANG_CODE --es date 2026-11-11T15:00:00Z --es appearance $appearance $extras >/dev/null
    sleep "$WAIT"
    $ADB exec-out screencap -p > "$OUT/$name.png"
    echo "$OUT/$name.png"
  done
done
$ADB shell cmd uimode night no >/dev/null
demo exit
