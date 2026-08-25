---
name: android-e2e-demo-recording
description: Run nowinandroid unit + instrumented (Compose) tests on a Devin box, and record a single continuous side-by-side demo video of them. Use when asked to run the Android tests, verify them on an emulator, or produce a recording/demo of a test run.
---

# nowinandroid tests on a Devin box: run them, and record them

Two jobs live here. **If you only need results, stop after [Run and verify](#run-and-verify)** —
everything below it is for producing a watchable demo and costs another ~10 minutes.

Scripts do the work; this file explains when and why. All paths are relative to the repo root.

## Read this first: the three traps

1. **The screen recorder time lapses its own `*-edited.mp4`** (a 200s take becomes ~18s). Always
   re-encode the raw `*-raw-*.mkv` chunks instead — `scripts/finalize_recording.sh` does this and
   `scripts/verify_evidence.sh --video` fails the take if the duration does not match the run.
2. **Compose journeys are near-instant.** A desktop capture at default speed shows each screen for a
   single frame, and a screenshot after the run only catches the launcher. The run must be slowed with
   `demoPauseMs` (below) or there is nothing to see.
3. **Gradle marks test tasks UP-TO-DATE**, so a recorded "run" can execute zero tests. Pass `--rerun`
   per task and always confirm against the result XML, never against the console.

Two smaller ones that cost a whole take each: the emulator must be started with `setsid` or it dies
with the shell that launched it (it looks like a clean boot followed by "no emulator window"), and
`clear` fails when `TERM` is unset, which aborts a `set -e` script mid-run.

## Cost and timing

| Phase | Cold | Warm |
| --- | --- | --- |
| Emulator boot | ~40s | — |
| Pre-warm build (`--prewarm`) | 5-10 min | ~30s |
| Unit tasks (2 modules) | ~1 min | ~2s |
| `:app:connectedDemoDebugAndroidTest` | ~4 min | ~1m10s |
| Full on-camera take at `demoPauseMs=1500` | — | ~3.5 min |

Always pre-warm off camera. An un-warmed take is a five minute video of a Gradle build.

## Prerequisites

Provisioned by the repo blueprint; verify rather than assume:
- JDK 21 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`). JDK 17 fails Robolectric/AGP with
  "Android SDK 36 requires Java 21".
- Android SDK 36 and a `test_device` AVD. The emulator needs KVM, so it must be launched via `sg kvm -c`
  (see `scripts/boot_emulator.sh`) — the session shell does not inherit the kvm group.
- `wmctrl`, `xdotool`, `ffmpeg`, `konsole` for the recording half.

## Run and verify

```bash
S=.agents/skills/android-e2e-demo-recording/scripts
$S/boot_emulator.sh                     # idempotent; waits for sys.boot_completed, not just adb
$S/run_demo_suite.sh --prewarm          # build everything first
$S/run_demo_suite.sh --pause-ms 0       # no pauses when you are not recording
$S/verify_evidence.sh
```

`verify_evidence.sh` is the definition of done for the results half:
- instrumented `TEST-*.xml` exists, 0 failures, 0 errors;
- every `testcase name=` is listed, so a filter that silently matched nothing is caught;
- the test *set* is diffed against `baseline_testcases.txt` rather than against a count hard coded in
  the doc, so the baseline cannot rot and a real regression cannot hide behind a stale number. That
  file is written on the first run and deliberately not committed: it must describe the branch you are
  on;
- unit XMLs report `failures="0" errors="0"`.

Verified on this box against `main`: 11 unit tests (`GetFollowableTopicsUseCaseTest`,
`SearchViewModelTest`) and 12 instrumented `NavigationTest` tests, 1 of which is skipped
(`navigationBar_multipleBackStackInterests`) — the console prints `Finished 13 tests` because it
counts the skip separately. `UserJourneysTest` and its pause hooks add 7 more once that work lands.

Result files, if you need them directly:
- unit: `<module>/build/test-results/testDemoDebugUnitTest/*.xml`
- instrumented: `app/build/outputs/androidTest-results/connected/debug/flavors/demo/TEST-*.xml`

Useful ad-hoc invocations:
- one class: `./gradlew :app:connectedDemoDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`
- one method: append `#methodName` to the same property (warm re-run ~15s)

## Record a demo

Deliverable: **exactly one** 1x mp4 showing the emulator and the Gradle console side by side, recorded
in one unbroken take. Extra device-only captures confuse reviewers.

```bash
S=.agents/skills/android-e2e-demo-recording/scripts
$S/boot_emulator.sh
$S/run_demo_suite.sh --prewarm          # off camera
$S/place_windows.sh                     # derives geometry from the actual display
# in the konsole that just opened, make it readable on video:
#   printf '\033]50;FontSize=16\a'; clear
# start the screen recording, then in that same konsole, and nothing else:
$S/run_demo_suite.sh --pause-ms 1500
# stop the recording
$S/finalize_recording.sh <screencast-dir> take_1x.mp4
$S/annotations_from_log.sh              # one annotation window per test
$S/verify_evidence.sh --video take_1x.mp4
```

### Why each piece exists

**Window placement is derived, not fixed.** The emulator window keeps the device aspect ratio: asking for
a wide window at full height yields something much narrower (504px wide at 1120px tall on a 1600x1200
desktop). `place_windows.sh` therefore requests full height, measures the result with `wmctrl -lG`, and
starts the terminal past the emulator's floating side-toolbar (a separate window — keep it clear). It
polls for windows instead of sleeping, so it does not race konsole's startup, and it works on a display
of any size.

**Pauses come from the tests, opt-in.** `UserJourneysTest` reads a `demoPauseMs` instrumentation argument:

```kotlin
private val demoPauseMs: Long =
    InstrumentationRegistry.getArguments().getString("demoPauseMs")?.toLongOrNull() ?: 0L

private fun demoPause() { if (demoPauseMs > 0) Thread.sleep(demoPauseMs) }

@After  // rests on each journey's end state so consecutive tests are distinguishable
fun holdFinalState() { if (demoPauseMs > 0) Thread.sleep(demoPauseMs * 2) }
```

It defaults to 0, so CI and normal runs are untouched; at 1500ms each journey takes ~8-11s and the whole
suite costs ~21s extra. Add the same three pieces to any other UI test class you want to demo, and call
`demoPause()` after each meaningful assertion — a journey with no pauses is invisible in the video.
File: `app/src/androidTest/kotlin/com/google/samples/apps/nowinandroid/ui/UserJourneysTest.kt`.

**Annotations are derived after the run, from timestamps.** `run_demo_suite.sh` starts an
`adb logcat -v epoch -s TestRunner:I` watcher and records the run's start epoch;
`annotations_from_log.sh` turns that into `<class> <method> <start-offset> <end-offset>`. Emit **one
`test_start` plus one grouped assertion per test** — the number of annotations is what the UI reports as
the test count, which is why phase-level markers once surfaced a 19-test run as "2 tests passed". Test
execution order is not source order; read it from the log.

Optional: the same offsets can drive an `ffmpeg drawtext` overlay of the running test name, making the
video self-documenting and independent of the annotation UI.

**Device-only capture** is an alternative when the terminal is not needed (crisper, but loses the
console): `adb shell screenrecord` writes ~180s per file, so loop segments and concat them.

```bash
i=0; touch /tmp/recflag
while [ -f /tmp/recflag ]; do
    adb shell screenrecord --time-limit 180 --bit-rate 8000000 "/sdcard/demo_seg_$i.mp4"
    i=$((i+1))
done
# stop with: rm /tmp/recflag; adb shell pkill -l 2 screenrecord   (SIGINT so the mp4 is finalised)
adb pull /sdcard/  ./device-segments
printf "file '%s'\n" ./device-segments/demo_seg_*.mp4 > /tmp/list.txt
ffmpeg -f concat -safe 0 -i /tmp/list.txt -c copy device.mp4
```

### Before you ship the video

`verify_evidence.sh --video take_1x.mp4` checks results plus duration against the measured wall clock.
Then eyeball the contact sheet that `finalize_recording.sh` wrote, and confirm each journey's end state
occupies ~3+ consecutive one-second frames:

```bash
ffmpeg -ss 95 -to 155 -i take_1x.mp4 -vf "fps=1,crop=520:1130:0:20,scale=150:-1,tile=15x4" -frames:v 1 sheet.png
```

Trim any leading idle with `-ss`. Ship one mp4.

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `/dev/kvm permission denied` | Launch through `sg kvm -c` (`boot_emulator.sh`), not directly. |
| `adb wait-for-device` returns but the screen is black | Boot is not finished; poll `sys.boot_completed`. |
| No emulator window on screen | `DISPLAY` unset; export `DISPLAY=:0` before emulator and wmctrl. |
| `adb` reports no devices after a box restart | Caches survive restarts, the emulator/konsole/adb server do not. Re-run boot + placement. |
| Test tasks print `UP-TO-DATE`, video shows nothing | Missing `--rerun` per task. |
| "configuration cache discarded" on connected tests | Expected (OSS-licenses task), not a failure. |
| `screenrecord` stops early | ~180s per file limit, and check free space on `/sdcard`. |
| Console floods with `preBuild UP-TO-DATE` | Use `grep -E`, not `-Ei`: case-insensitive `BUILD` matches everything. |
| Windows overlap or hide under the panel | Re-run `place_windows.sh`; it re-measures and re-places. |

## Who runs this

The testing agent owns the whole procedure (boot, build, windowing, recording) and hands back:
the single `take_1x.mp4` path, the `verify_evidence.sh` output, and the annotation list. The main agent
attaches those to the user or the PR — it should not re-run any of this itself.

## Devin Secrets Needed

None.
