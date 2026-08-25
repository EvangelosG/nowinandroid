#!/usr/bin/env bash
# Boot the test_device AVD and block until Android has finished booting.
# Usage: boot_emulator.sh [--boot-anim]
set -euo pipefail

AVD="${AVD:-test_device}"
BOOT_ANIM_FLAG="-no-boot-anim"
[ "${1:-}" = "--boot-anim" ] && BOOT_ANIM_FLAG=""

export DISPLAY="${DISPLAY:-:0}"
: "${ANDROID_HOME:?ANDROID_HOME is not set}"

if adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' | grep -qx 1; then
    echo "emulator already booted"
    exit 0
fi

# KVM group membership is not inherited by the session shell, hence sg.
# setsid, not just nohup: without its own session the emulator is killed when the
# shell that launched it exits, which looks exactly like a successful boot
# followed by "no emulator window".
sg kvm -c "setsid $ANDROID_HOME/emulator/emulator -avd $AVD -no-snapshot -gpu swiftshader_indirect $BOOT_ANIM_FLAG >/tmp/emulator.log 2>&1 < /dev/null &"

adb wait-for-device
# wait-for-device returns long before Android is usable; boot takes ~40s.
timeout 300 bash -c 'until [ "$(adb shell getprop sys.boot_completed | tr -d "\r")" = "1" ]; do sleep 5; done' || {
    echo "emulator did not finish booting; last 40 lines of /tmp/emulator.log:" >&2
    tail -40 /tmp/emulator.log >&2
    exit 1
}
adb shell input keyevent 82 >/dev/null 2>&1 || true
echo "emulator booted"
