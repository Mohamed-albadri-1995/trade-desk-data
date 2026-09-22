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

# However this ends, the log and the last thing on the screen are kept. A
# check that fails without saying what the phone was doing is no check at all.
keep_evidence() {
  adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
  adb exec-out screencap -p > "$OUT/zz-at-the-end.png" 2>/dev/null || true
  adb shell dumpsys activity activities > "$OUT/activities.txt" 2>/dev/null || true
  echo "─── the last lines the system had about the book ───"
  grep -iE "$PKG|AndroidRuntime|FATAL|ActivityManager.*dalail" "$OUT/logcat.txt" \
    2>/dev/null | tail -40 || true
}
trap keep_evidence EXIT

# Which activity the system says is on screen. The wording changed between
# Android versions, so both are asked for.
on_screen() {
  adb shell dumpsys activity activities \
    | grep -E "mResumedActivity|topResumedActivity" || true
}

in_the_book() { on_screen | grep -q "$PKG"; }

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }

echo "═══ install ═══"
adb logcat -c || true
adb install -r "$APK"

# Granted here rather than left to the dialog. The book asks for it on its
# first launch on Android 13 and later, and the system's own permission screen
# then stands in front of the leaf — which is the app behaving correctly, but
# it is not what this is looking at. Harmless where it does not exist.
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

echo "═══ open the book ═══"
adb shell am start -W -n "$PKG/.MainActivity" | tee /tmp/start.txt
grep -q "Status: ok" /tmp/start.txt || { echo "FAIL: it would not start"; exit 1; }

# Waited for rather than assumed: a cold start on a software-rendered emulator
# can take a while, and a fixed sleep either wastes time or gives up too soon.
echo "waiting for the leaf to come up"
settled=no
for _ in $(seq 1 20); do
  if in_the_book; then settled=yes; break; fi
  sleep 2
done
on_screen
if [ "$settled" != yes ]; then
  echo "FAIL: the book did not stay on screen — the log is below"
  exit 1
fi
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

echo "═══ back: the day's card first, the book only after it ═══"
# The card offering today's حزب is up from the moment the book opens, and
# back is meant to put that away before it leaves — so the first press should
# find us still in the book.
adb shell input keyevent KEYCODE_BACK
sleep 3
if in_the_book; then
  echo "still in the book after the first back, as it should be"
  adb shell input keyevent KEYCODE_BACK
  sleep 4
else
  echo "the first back left the book — the card must have been dismissed already"
fi
if in_the_book; then
  on_screen
  echo "FAIL: back did not leave the book"
  exit 1
fi
echo "left it"

echo "═══ open it again — it should come back to the leaf we were on ═══"
adb shell am start -W -n "$PKG/.MainActivity" >/dev/null
sleep 2
settled=no
for _ in $(seq 1 15); do
  if in_the_book; then settled=yes; break; fi
  sleep 2
done
if [ "$settled" != yes ]; then
  echo "FAIL: it would not open a second time"
  exit 1
fi
shot 4-reopened
echo "opened again"

echo "═══ did anything crash, hang, or get killed? ═══"
adb logcat -d > "$OUT/logcat.txt" || true
if grep -E "FATAL EXCEPTION|ANR in $PKG|Force finishing.*$PKG" "$OUT/logcat.txt"; then
  echo "FAIL: see above"
  exit 1
fi
echo "nothing"

echo "═══ all well ═══"
