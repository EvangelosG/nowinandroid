#!/usr/bin/env bash
# Derive one annotation window per test from the instrumentation log, AFTER the
# run. Racing the run with sleep loops mis-attributes journeys; timestamps do not.
#
# Usage: annotations_from_log.sh [--log /tmp/journeys.log] [--epoch-file /tmp/demo_run_start_epoch]
# Output: <class> <method> <start-offset-s> <end-offset-s>, offsets relative to
# the start of the recording, ready to drive annotate_recording or ffmpeg drawtext.
set -euo pipefail

LOG=/tmp/journeys.log
EPOCH_FILE=/tmp/demo_run_start_epoch
while [ $# -gt 0 ]; do
    case "$1" in
        --log) LOG="$2"; shift 2 ;;
        --epoch-file) EPOCH_FILE="$2"; shift 2 ;;
        *) echo "unknown arg: $1" >&2; exit 2 ;;
    esac
done

[ -f "$LOG" ] || { echo "$LOG missing: start the logcat watcher before the run" >&2; exit 1; }
BASE=$(cat "$EPOCH_FILE" 2>/dev/null || awk 'NR==1{print int($1)}' "$LOG")

# adb logcat -v epoch lines look like:
#   1756061234.123  1234  1240 I TestRunner: started: aTest(com.example.BarTest)
# Normalise to "<epoch> <started|finished> <class> <method>" first; mawk has no
# three argument match(), so the parsing lives in sed.
sed -nE 's/^[[:space:]]*([0-9]+)\.[0-9]+.*(started|finished): *[0-9]*:? *([A-Za-z_0-9]+)\(([A-Za-z0-9_.]+)\).*/\1 \2 \4 \3/p' "$LOG" |
awk -v base="$BASE" '
    { key = $3 "\t" $4
      if ($2 == "started") { if (!(key in start)) { start[key] = $1 - base; order[++n] = key } }
      else { end[key] = $1 - base } }
    END { for (i = 1; i <= n; i++) { k = order[i]
              printf "%s\t%s\t%s\n", k, start[k], (k in end ? end[k] : "?") } }
'

cat >&2 <<'EOF'

One annotation per test above: test_start at the start offset, a single grouped
assertion at the end offset. Do not annotate per phase; the run reports as many
tests as you annotate, so phase level markers show up as "2 tests passed".
Execution order is not source order, always read it from this log.
EOF
