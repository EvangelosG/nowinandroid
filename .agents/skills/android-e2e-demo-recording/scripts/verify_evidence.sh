#!/usr/bin/env bash
# Definition of done for a demo run. Prints the evidence and fails on anything
# that has previously shipped broken: zero-match test filters, a changed test
# set, or a video whose duration does not match the measured run.
# It cannot judge watchability: check the contact sheet by eye for that.
#
# Usage: verify_evidence.sh [--results-only] [--video take_1x.mp4]
set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$SCRIPT_DIR/../../../.." || exit 1

RESULTS_ONLY=0
VIDEO=""
while [ $# -gt 0 ]; do
    case "$1" in
        --results-only) RESULTS_ONLY=1; shift ;;
        --video) VIDEO="$2"; shift 2 ;;
        *) echo "unknown arg: $1" >&2; exit 2 ;;
    esac
done

FAIL=0
note() { echo "  $*"; }
bad() { echo "FAIL: $*" >&2; FAIL=1; }

X=$(ls app/build/outputs/androidTest-results/connected/debug/flavors/demo/TEST-*.xml 2>/dev/null | head -1)
if [ -z "$X" ]; then
    bad "no instrumented result XML; the run never reached the device"
else
    echo "instrumented:"
    note "$(grep -o 'tests="[0-9]*" failures="[0-9]*" errors="[0-9]*" skipped="[0-9]*" time="[0-9.]*"' "$X")"
    grep -o 'testcase name="[a-zA-Z_0-9]*" classname="[a-zA-Z.]*"' "$X" |
        sed 's/.*name="\([a-zA-Z_0-9]*\)" classname="[a-z.]*\([A-Za-z]*\)"/  \2 :: \1/' | sort
    TESTS=$(grep -o 'tests="[0-9]*"' "$X" | head -1 | tr -dc 0-9)
    FAILURES=$(grep -o 'failures="[0-9]*"' "$X" | head -1 | tr -dc 0-9)
    ERRORS=$(grep -o 'errors="[0-9]*"' "$X" | head -1 | tr -dc 0-9)
    # Named test counts are asserted against the previous run, not a constant in
    # this file, so the baseline cannot silently rot.
    BASELINE="${BASELINE:-.agents/skills/android-e2e-demo-recording/baseline_testcases.txt}"
    CURRENT=$(grep -o 'testcase name="[a-zA-Z_0-9]*"' "$X" | sort)
    if [ -f "$BASELINE" ]; then
        if ! diff -q <(echo "$CURRENT") "$BASELINE" >/dev/null; then
            echo "test set differs from the recorded baseline:"
            diff "$BASELINE" <(echo "$CURRENT") | sed 's/^/  /'
            bad "expected test set changed; update $BASELINE deliberately if this is intended"
        fi
    else
        echo "$CURRENT" > "$BASELINE"
        note "wrote first baseline to $BASELINE ($TESTS tests)"
    fi
    if [ "${FAILURES:-1}" != 0 ] || [ "${ERRORS:-1}" != 0 ]; then
        bad "instrumented run had failures/errors"
    fi
fi

echo "unit:"
for f in core/domain/build/test-results/testDemoDebugUnitTest/*.xml \
         feature/search/impl/build/test-results/testDemoDebugUnitTest/*.xml; do
    [ -f "$f" ] || continue
    note "$(head -2 "$f" | grep -o 'name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"')"
    head -2 "$f" | grep -q 'failures="0" errors="0"' || bad "unit failures in $f"
done

if [ "$RESULTS_ONLY" = 1 ]; then
    [ "$FAIL" = 0 ] && echo "OK: results complete (video not checked)"
    exit "$FAIL"
fi

if [ -n "$VIDEO" ]; then
    echo "video:"
    [ -f "$VIDEO" ] || { bad "$VIDEO does not exist"; exit 1; }
    DUR=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$VIDEO" | cut -d. -f1)
    note "$VIDEO duration ${DUR}s, $(ffprobe -v error -select_streams v:0 -show_entries stream=width,height,avg_frame_rate -of csv=p=0 "$VIDEO")"
    if [ -f /tmp/demo_run_start_epoch ] && [ -f /tmp/demo_run_end_epoch ]; then
        WALL=$(( $(cat /tmp/demo_run_end_epoch) - $(cat /tmp/demo_run_start_epoch) ))
        note "measured run wall clock ${WALL}s"
        # The screen recorder time lapses its own -edited.mp4; a video much
        # shorter than the run is that bug, not a fast test suite.
        [ "${DUR:-0}" -lt $((WALL * 8 / 10)) ] && bad "video is ${DUR}s for a ${WALL}s run: this is the time lapsed export, re encode the raw chunks"
        # The opposite failure: shipping a take padded with idle, or the wrong file.
        [ "${DUR:-0}" -gt $((WALL * 2)) ] && bad "video is ${DUR}s for a ${WALL}s run: trim the idle head/tail, or you concatenated the wrong chunks"
    fi
fi

[ "$FAIL" = 0 ] && echo "OK: evidence complete"
exit "$FAIL"
