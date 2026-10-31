#!/usr/bin/env bash
# Takes screenshots of a debug build in an emulator, with sample data.
# Run by .github/workflows/screenshots.yml while an emulator is booted.
set -euo pipefail

PKG=com.rachitratnammudaliar.renewly
OUT=docs/screenshots
mkdir -p "$OUT"

./gradlew --no-daemon installDebug

# Clean status bar: fixed clock, full battery, no notifications.
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941 >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null

shot() {
  local screen=$1 name=$2
  adb shell am start -S -W -n "$PKG/.MainActivity" --ez renewly.demo true --es renewly.screen "$screen" >/dev/null
  sleep 6
  adb exec-out screencap -p > "$OUT/$name.png"
  echo "Saved $OUT/$name.png"
}

adb shell cmd uimode night no
shot list home
shot detail:1 detail
shot edit:1 edit
shot settings settings

adb shell cmd uimode night yes
sleep 2
shot list home-dark
adb shell cmd uimode night no
