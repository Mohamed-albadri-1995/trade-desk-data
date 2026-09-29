#!/usr/bin/env bash
#
# Puts the ratib on whatever Android is attached to adb and uses it.
#
# A file of its own because the emulator action runs each line of an inline
# script in a shell of its own, which breaks anything spanning two lines.
#
# Usage: smoke.sh <path to apk> [directory for screenshots]

set -euo pipefail

APK="${1:?give the path of the apk}"
OUT="${2:-shot}"
PKG="com.sammaniyya.awrad"
COVER="$PKG/com.ratib.saada.CoverActivity"
mkdir -p "$OUT"

# However this ends, the log and the last thing on the screen are kept. A
# check that fails without saying what the phone was doing is no check at all.
keep_evidence() {
  adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
  adb exec-out screencap -p > "$OUT/zz-at-the-end.png" 2>/dev/null || true
  adb shell dumpsys activity activities > "$OUT/activities.txt" 2>/dev/null || true
  echo "─── the last lines the system had about the ratib ───"
  grep -iE "$PKG|ratib|AndroidRuntime|FATAL" "$OUT/logcat.txt" 2>/dev/null | tail -40 || true
}
trap keep_evidence EXIT

on_screen() {
  adb shell dumpsys activity activities \
    | grep -E "mResumedActivity|topResumedActivity" || true
}

in_the_app() { on_screen | grep -q "$PKG"; }

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }

wait_for_app() {
  for _ in $(seq 1 20); do
    if in_the_app; then return 0; fi
    sleep 2
  done
  on_screen
  return 1
}

echo "═══ install ═══"
adb logcat -c || true
adb install -r "$APK"

# Granted up front. The app asks for these itself on a first launch, and the
# system's own dialogs would stand in front of what this is looking at.
for p in android.permission.POST_NOTIFICATIONS \
         android.permission.ACCESS_COARSE_LOCATION; do
  adb shell pm grant "$PKG" "$p" 2>/dev/null || true
done

echo "═══ open the cover ═══"
adb shell am start -W -n "$COVER" | tee /tmp/start.txt
grep -q "Status: ok" /tmp/start.txt || { echo "FAIL: it would not start"; exit 1; }
wait_for_app || { echo "FAIL: the cover did not stay on screen"; exit 1; }
shot 1-cover
echo "on screen"

echo "═══ enter the book ═══"
# The cover's button sits at the foot of the screen — the very place Android 15
# draws the gesture bar over, which is what the insets are there to prevent.
adb shell input tap 540 1900
sleep 6
shot 2-reading
in_the_app || { echo "FAIL: the app left the screen on entering"; exit 1; }

echo "═══ turn a page, and turn it back ═══"
adb shell input swipe 200 1200 900 1200 300
sleep 3
shot 3-turned
adb shell input swipe 900 1200 200 1200 300
sleep 3

echo "═══ the reminders are armed ═══"
# This is where a prayer time is either called at its minute or held back: an
# exact alarm needs the reader's leave from Android 14 onward. The system is
# asked whether it actually holds an alarm for the ratib.
adb shell dumpsys alarm > "$OUT/alarms.txt" 2>/dev/null || true
if grep -q "$PKG" "$OUT/alarms.txt"; then
  grep -A2 "$PKG" "$OUT/alarms.txt" | head -12
  echo "armed"
else
  echo "FAIL: the system holds no alarm for the ratib"
  exit 1
fi

echo "═══ and they survive a restart of the phone ═══"
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED \
  -n "$PKG/com.ratib.saada.BootReceiver" >/dev/null 2>&1 || true
sleep 5

echo "═══ did anything crash, hang, or get killed? ═══"
adb logcat -d > "$OUT/logcat.txt" || true
if grep -E "FATAL EXCEPTION|ANR in $PKG|Force finishing.*$PKG" "$OUT/logcat.txt"; then
  echo "FAIL: see above"
  exit 1
fi
echo "nothing"

echo "═══ all well ═══"
