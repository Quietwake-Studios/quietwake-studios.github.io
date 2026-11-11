#!/usr/bin/env bash
# App Store screenshots: iPhone 6.9" and iPad 13" × English/Spanish × light/dark, on the
# `-demo` state (App/Debug/DemoData.swift), to AppStore/screenshots/<device>/<language>/<appearance>/NN-name.png
# (scripts/store-images.py frames them). Spanish shots serve es-MX. The words' language
# follows the shot's language (`-words`).
#
#   scripts/screenshots.sh                 # everything
#   scripts/screenshots.sh ipad            # only some devices (iphone, ipad)
#   LANGUAGES="es" APPEARANCES="dark" scripts/screenshots.sh iphone
#
# Each device gets its own simulator ("QW Shots iPhone 6.9", "QW Shots iPad 13"), created for
# the run and deleted by udid at the end. Sound is off on it (the app's tones need the Mac's
# audio, and a hung coreaudiod crashes the app on the first tone). Don't run it while another
# app is capturing.
set -euo pipefail
cd "$(dirname "$0")/.."
BUNDLE=com.tavoohoh.quietwords
APP="build/dd/Build/Products/Debug-iphonesimulator/Quiet Words.app"

DEVICES=("${@:-iphone ipad}")
read -r -a DEVICES <<< "${DEVICES[*]}"
read -r -a LANGUAGES <<< "${LANGUAGES:-en es}"
read -r -a APPEARANCES <<< "${APPEARANCES:-light dark}"

# name|seconds to wait|launch arguments (the summary sheet comes after the reveal animation).
SCREENS=(
  "01-game|8|-demo -completeToday -startGame -seedKey quiet-store -level medium -theme ocean -found 7 -demoClock 192"
  "02-daily|8|-demo -completeToday -open daily"
  "03-leaderboards|8|-demo -completeToday -open leaderboards"
  "04-album|8|-demo -completeToday -forceLookUp -albumTheme ocean"
  "05-hidden|16|-demo -completeToday -startGame -seedKey quiet-win -level medium -theme space -nearlyWon -won -demoClock 248"
  "06-home|8|-demo -completeToday"
)

RUNTIME="$(xcrun simctl list runtimes -j | python3 -c '
import json, sys
ios = [r for r in json.load(sys.stdin)["runtimes"] if r["platform"] == "iOS" and r["isAvailable"]]
print(ios[-1]["identifier"])')"

xcodebuild build -project QuietWords.xcodeproj -scheme QuietWords -destination 'generic/platform=iOS Simulator' \
    -derivedDataPath build/dd -quiet

UDID=""
cleanup() { [[ -n "$UDID" ]] && { xcrun simctl shutdown "$UDID" 2>/dev/null || true; xcrun simctl delete "$UDID" 2>/dev/null || true; }; }
trap cleanup EXIT

shoot() { # folder-name simulator-name devicetype
    UDID="$(xcrun simctl create "$2" "$3" "$RUNTIME")"
    xcrun simctl boot "$UDID"
    xcrun simctl bootstatus "$UDID" >/dev/null
    xcrun simctl install "$UDID" "$APP"
    xcrun simctl spawn "$UDID" defaults write "$BUNDLE" soundEnabled -bool NO
    xcrun simctl status_bar "$UDID" override --time 9:41 --dataNetwork wifi --wifiBars 3 \
        --cellularMode active --cellularBars 4 --batteryState discharging --batteryLevel 100
    # A first launch on a fresh simulator is slow and iOS shows its own banners: warm up.
    # iOS shows its own banners (e.g. "Ready for Apple Intelligence") a while after the first boot.
    xcrun simctl launch "$UDID" "$BUNDLE" -demo >/dev/null
    sleep 30
    xcrun simctl terminate "$UDID" "$BUNDLE" 2>/dev/null || true
    xcrun simctl launch "$UDID" "$BUNDLE" -demo >/dev/null
    sleep 15
    for appearance in "${APPEARANCES[@]}"; do
        xcrun simctl ui "$UDID" appearance "$appearance"
        for language in "${LANGUAGES[@]}"; do
            local out="AppStore/screenshots/$1/$language/$appearance" locale
            [[ "$language" == es ]] && locale=es_MX || locale=en_US
            mkdir -p "$out"
            echo "▸ $out"
            for entry in "${SCREENS[@]}"; do
                local name wait args
                IFS='|' read -r name wait args <<< "$entry"
                xcrun simctl terminate "$UDID" "$BUNDLE" 2>/dev/null || true
                # shellcheck disable=SC2086
                xcrun simctl launch "$UDID" "$BUNDLE" $args -words "$language" -AppleLanguages "($language)" -AppleLocale "$locale" >/dev/null
                sleep "$wait"
                xcrun simctl io "$UDID" screenshot "$out/$name.png" >/dev/null 2>&1
            done
        done
    done
    cleanup
    UDID=""
}

for device in "${DEVICES[@]}"; do
    case "$device" in
        iphone) shoot iphone-6.9 "QW Shots iPhone 6.9" com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro-Max ;;
        ipad) shoot ipad-13 "QW Shots iPad 13" com.apple.CoreSimulator.SimDeviceType.iPad-Pro-13-inch-M5-12GB ;;
        *) echo "Unknown device: $device (iphone, ipad)" >&2; exit 1 ;;
    esac
done
echo "Saved to AppStore/screenshots/"
