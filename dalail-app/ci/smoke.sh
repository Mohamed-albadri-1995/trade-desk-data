#!/usr/bin/env bash
#
# Puts the book on whatever Android is attached to adb and uses it.
#
# This lives in a file of its own because the emulator action runs each line
# of an inline script as a separate shell, which breaks anything spanning more
# than one line — a continuation, an if, a loop. Here the whole thing is one
# command to it.
#
# Usage: smoke.sh <path to apk> [directory for screenshots]

set -euo pipefail

APK="${1:?give the path of the apk}"
OUT="${2:-shot}"
PKG="com.dalail.rahamat"
mkdir -p "$OUT"

# Which activity the system says is on screen. The wording changed between
# Android versions, so both are asked for.
on_screen() {
  adb shell dumpsys activity activities \
    | grep -E "mResumedActivity|topResumedActivity" || true
}

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }

echo "═══ install ═══"
adb logcat -c || true
adb install -r "$APK"

# Granted here rather than left to the dialog. The book asks for it on its
# first launch on Android 13 and later, and the system's own permission
# screen then stands in front of the leaf — which is the app behaving
# correctly, but it is not what this is looking at. Harmless where the
# permission does not exist.
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

echo "═══ open the book ═══"
adb shell am start -W -n "$PKG/.MainActivity" | tee /tmp/start.txt
grep -q "Status: ok" /tmp/start.txt || { echo "FAIL: it would not start"; exit 1; }
sleep 12
on_screen | tee /tmp/top.txt
grep -q "$PKG" /tmp/top.txt || { echo "FAIL: the book is not on screen"; exit 1; }
shot 1-opened
echo "on screen"

echo "═══ turn a leaf and turn it back ═══"
# The book is turned right to left, so a swipe left to right brings the next
# leaf. Then the same the other way, to come back to where we were.
adb shell input swipe 200 1200 900 1200 300
sleep 3
shot 2-turned
adb shell input swipe 900 1200 200 1200 300
sleep 3

echo "═══ tap the leaf, which puts the bars aside and brings them back ═══"
adb shell input tap 540 1200
sleep 2
shot 3-bars-aside
adb shell input tap 540 1200
sleep 2

echo "═══ back leaves the book ═══"
adb shell input keyevent KEYCODE_BACK
sleep 4
on_screen | tee /tmp/top2.txt
# if/then, not «grep && exit»: when grep finds nothing — which is the pass
# here — it returns a failure, and set -e would kill the script on it.
if grep -q "$PKG" /tmp/top2.txt; then
  echo "FAIL: back did not leave the book"
  exit 1
fi
echo "left it"

echo "═══ open it again — it should come back to the leaf we were on ═══"
adb shell am start -W -n "$PKG/.MainActivity" >/dev/null
sleep 8
on_screen | tee /tmp/top3.txt
grep -q "$PKG" /tmp/top3.txt || { echo "FAIL: it would not open a second time"; exit 1; }
shot 4-reopened
echo "opened again"

echo "═══ did anything crash, hang, or get killed? ═══"
adb logcat -d > "$OUT/logcat.txt" || true
if grep -E "FATAL EXCEPTION|ANR in $PKG|Force finishing.*$PKG" "$OUT/logcat.txt"; then
  echo "FAIL: see above"
  exit 1
fi
echo "nothing"

echo "═══ what the system had to say about the book ═══"
grep -iE "$PKG|dalail" "$OUT/logcat.txt" | tail -30 || true

echo "═══ all well ═══"
