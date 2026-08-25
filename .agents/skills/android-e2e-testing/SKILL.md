---
name: android-e2e-testing
description: How to run and record Now in Android unit + instrumented (Compose) tests on a headless Devin box with a visible emulator.
---

# Running / demoing nowinandroid tests on a Devin box

## Prerequisites (usually already provisioned by the repo blueprint)
- JDK 21 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`). JDK 17 fails Robolectric/AGP with
  "Android SDK 36 requires Java 21".
- Android SDK 36 + `test_device` AVD; emulator needs KVM, so always launch through `sg kvm -c`.

## Emulator
```
sg kvm -c "nohup $ANDROID_HOME/emulator/emulator -avd test_device -no-snapshot -gpu swiftshader_indirect >/tmp/emulator.log 2>&1 &"
adb wait-for-device
```
Omit `-no-boot-anim` when you want the boot to be watchable in a recording. Boot takes ~40s.
The emulator window appears on `DISPLAY=:0` titled `Android Emulator - test_device:5554`; it
honours `wmctrl -r "<title>" -e 0,x,y,w,h`, so you can enlarge it (e.g. `0,20,50,560,1120`) and
place a `konsole` window beside it for a side-by-side terminal + device recording.

## Commands
- Unit tests (module-scoped is much faster than `testDemoDebug`):
  `./gradlew :core:domain:testDemoDebugUnitTest --rerun :feature:search:impl:testDemoDebugUnitTest --rerun`
  Gradle marks test tasks UP-TO-DATE on re-runs — pass `--rerun` per task so the demo actually
  executes them.
- Instrumented (whole class): `./gradlew :app:connectedDemoDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`
- Single method: append `#methodName` to the same property. Warm re-runs take ~15s each, which
  makes per-test annotated recordings practical.
- Filter noisy Gradle output with `--console=plain ... | grep -Ei 'Starting|Tests |Finished|BUILD|FAILED'`.

## Evidence / result files
- Unit: `<module>/build/test-results/testDemoDebugUnitTest/*.xml` (`tests=`/`failures=` in the
  `<testsuite>` line).
- Instrumented: `app/build/outputs/androidTest-results/connected/debug/flavors/demo/TEST-*.xml`
  — grep `testcase name="..."` to prove every expected test actually ran (guards against a
  class/method filter silently matching nothing).

## Recording the device screen (much better than desktop capture)
Compose instrumented journeys are near-instant, so a desktop capture of the emulator shows each
state for a single frame. Two things fix this:

1. `UserJourneysTest` supports an opt-in hold: pass
   `-Pandroid.testInstrumentationRunnerArguments.demoPauseMs=1500`. It is 0 (no-op) by default, so
   CI is unaffected. Consider the same `demoPause()` pattern for other demoable UI tests.
2. Capture the device framebuffer directly:
```
# segments, because screenrecord stops at ~180s per file
touch /tmp/recflag
while [ -f /tmp/recflag ]; do adb shell screenrecord --time-limit 180 --bit-rate 8000000 /sdcard/demo_seg_$i.mp4; i=$((i+1)); done
# stop with: rm /tmp/recflag; adb shell pkill -l 2 screenrecord   (SIGINT so the mp4 is finalised)
adb pull /sdcard/demo_seg_00.mp4 .
ffmpeg -f concat -safe 0 -i list.txt -c copy full.mp4   # when there are 2+ segments
```
Trim the leading Gradle-overhead idle (~15s of launcher) with `-ss`, and sanity check coverage
with a contact sheet: `ffmpeg -i v.mp4 -vf "fps=1/6,scale=270:-1,tile=6x2" -frames:v 1 sheet.png`.

## One continuous side-by-side take (exact, verified recipe)
Deliverable: ONE desktop mp4 at 1x showing the emulator and the Gradle console together. Verified
on a 1600x1200 `DISPLAY=:0` Plasma desktop.

1. Boot the emulator (see above) and wait for boot to actually complete, not just `wait-for-device`:
```
export DISPLAY=:0
sg kvm -c "nohup $ANDROID_HOME/emulator/emulator -avd test_device -no-snapshot -gpu swiftshader_indirect -no-boot-anim >/tmp/emulator.log 2>&1 &"
adb wait-for-device
timeout 240 bash -c 'until [ "$(adb shell getprop sys.boot_completed | tr -d "\r")" = "1" ]; do sleep 5; done'
```

2. Open the terminal that will be on camera and place both windows (close/minimise anything else,
e.g. `wmctrl -c "New Tab - Google Chrome for Testing"`):
```
nohup konsole --workdir /home/ubuntu/repos/nowinandroid >/dev/null 2>&1 &
sleep 6
wmctrl -r "Android Emulator - test_device:5554" -e 0,8,28,700,1120
wmctrl -r "nowinandroid : bash — Konsole"        -e 0,575,28,1015,1120
wmctrl -lG   # verify
```
The emulator window **keeps the device aspect ratio**: asking for width 700 at height 1120 yields
504x1120. At 1200px screen height ~504px wide (~32% of a 1600px desktop) is the largest the
emulator pane can get — don't fight it, just give it full height and start the terminal at x≈575
(x 512..566 is the emulator's floating side-toolbar window, keep it clear). Leave y=28 and
height≤1120 so neither window sits under the Plasma taskbar.

3. Make the console readable in video — inside the konsole itself (escape sequence, no GUI needed):
```
printf '\033]50;FontSize=16\a'; clear
```

4. Pre-warm off camera so the take isn't a silent build: `./gradlew :app:assembleDemoDebug
:app:assembleDemoDebugAndroidTest :core:domain:testDemoDebugUnitTest
:feature:search:impl:testDemoDebugUnitTest`.

5. Start the screen recording, then run these in the visible konsole, never stopping the recording:
```
clear; ./gradlew :core:domain:testDemoDebugUnitTest --rerun :feature:search:impl:testDemoDebugUnitTest --rerun --console=plain 2>&1 \
  | grep -E 'Task :[a-z:]*UnitTest$|BUILD SUCCESSFUL|BUILD FAILED|FAILURE'

clear; for f in core/domain/build/test-results/testDemoDebugUnitTest/*Search*.xml feature/search/impl/build/test-results/testDemoDebugUnitTest/*SearchViewModel*.xml; do
  head -2 "$f" | grep -o 'name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"'; done

clear; ./gradlew :app:connectedDemoDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.demoPauseMs=1500 --console=plain 2>&1 \
  | grep -E 'Starting [0-9]+ tests|Tests [0-9]+/|Finished [0-9]+ tests|BUILD SUCCESSFUL|BUILD FAILED|FAILURE|failed'

clear; X=$(ls app/build/outputs/androidTest-results/connected/debug/flavors/demo/TEST-*.xml)
grep -o 'tests="[0-9]*" failures="[0-9]*" errors="[0-9]*" skipped="[0-9]*" time="[0-9.]*"' "$X"
grep -o 'testcase name="[a-zA-Z_0-9]*" classname="[a-zA-Z.]*"' "$X" \
  | sed 's/.*name="\([a-zA-Z_0-9]*\)" classname="[a-z.]*\([A-Za-z]*\)"/\2 :: \1/' | sort
```
Use `grep -E`, not `-Ei`: a case-insensitive `BUILD` matches every `preBuild UP-TO-DATE` line and
floods the screen. The whole take is ~3.5 minutes (unit ~2s, connected suite ~1m10s warm).

5b. **Per-test annotations** (so the testing UI reports one result per journey, not one per
phase). The Gradle console only prints counts, so follow the instrumentation log instead: start a
watcher *before* the recording and poll it between annotations:
```
adb logcat -c
nohup bash -c "adb logcat -s TestRunner:I | stdbuf -oL grep -E 'started:|finished:' > /tmp/journeys.log" >/dev/null 2>&1 &
# poll: block until the Nth journey has started, then annotate
for i in $(seq 1 40); do n=$(grep -c "started:.*UserJourneysTest" /tmp/journeys.log); [ "$n" -ge 2 ] && break; sleep 2; done
tail -2 /tmp/journeys.log
```
`NavigationTest` runs first, `UserJourneysTest` second; with `demoPauseMs=1500` each journey takes
~8–11s, which is enough time to close the previous assertion and open the next `test_start`. Kill
the watcher (`pkill -f "adb logcat -s TestRunner"`) when done. Journey execution order is not
source order — always read the actual name from the log rather than assuming.

5c. `UserJourneysTest` also has an `@After holdFinalState()` that sleeps `demoPauseMs * 2`, so each
journey's end state rests on screen (~3s at 1500ms) before the app is torn down. Verify it landed
in the video with a 1 fps contact sheet of the journeys region:
`ffmpeg -ss 95 -to 155 -i take_1x.mp4 -vf "fps=1,crop=520:1130:0:20,scale=150:-1,tile=15x4" -frames:v 1 sheet.png`
— each end state should occupy ~5 consecutive one-second frames.

6. Stop the recording, then produce the single 1x file. The recorder time-lapses its `-edited.mp4`
(~200s take → ~18s), so re-encode its own raw chunks (they are one continuous capture, split only
by the recorder):
```
cd <screencast dir>
printf "file '%s'\n" $(ls *-raw-*.mkv | sort) > raws.txt
ffmpeg -f concat -safe 0 -i raws.txt -c:v libx264 -preset veryfast -crf 26 -pix_fmt yuv420p take_1x.mp4
ffmpeg -i take_1x.mp4 -vf "fps=1/12,scale=320:-1,tile=6x3" -frames:v 1 sheet.png   # coverage check
```
Ship exactly one file (`take_1x.mp4`); extra device-only captures confuse reviewers.

## Gotchas
- Compose instrumented journeys execute in a few seconds each; a single screenshot after the run
  will only catch the launcher. Screenshot repeatedly starting ~10s after launching Gradle, or run
  tests one method at a time, to capture the app UI.
- `:app:connectedDemoDebugAndroidTest` discards the configuration cache (OSS-licenses task); this
  is expected and not a failure.
- The full `:app` connected suite is 19 tests (12 `NavigationTest` + 7 `UserJourneysTest`); the
  console prints `Finished 20 tests` because it counts the one skipped
  `NavigationTest.navigationBar_multipleBackStackInterests` separately. 1 skip is the expected
  baseline, not a regression from this PR.
- The box can be restarted between sessions: the repo/Gradle caches survive but the emulator,
  konsole and adb server do not. Re-run the boot + window-placement steps before recording.

## Devin Secrets Needed
None.
