#!/usr/bin/env bash
# Draw the name of the running test onto the video, one label per test window.
#
# The names are not written anywhere: they come from annotations_from_log.sh,
# which reads whatever tests the device actually ran. This exists because
# annotate_recording can only mark a LIVE recording at the current moment, so
# tests shorter than ~10s cannot be labelled that way.
#
# Usage: label_video.sh <in.mp4> <out.mp4> [--epoch-file /tmp/recording_start_epoch]
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
IN="${1:?usage: label_video.sh <in.mp4> <out.mp4> [--epoch-file FILE]}"
OUT="${2:?usage: label_video.sh <in.mp4> <out.mp4> [--epoch-file FILE]}"
shift 2

ANN=$("$SCRIPT_DIR/annotations_from_log.sh" "$@" 2>/dev/null)
[ -n "$ANN" ] || { echo "no test windows in the log; nothing to label" >&2; exit 1; }

FILTER=""
while IFS=$'\t' read -r _class method start end; do
    [ "$end" = "?" ] && continue
    [ -n "$FILTER" ] && FILTER="$FILTER,"
    FILTER="$FILTER drawtext=text='${method//\'/}':x=20:y=20:fontsize=28:fontcolor=white"
    FILTER="$FILTER:box=1:boxcolor=black@0.6:enable='between(t,$start,$end)'"
done <<< "$ANN"

ffmpeg -y -i "$IN" -vf "$FILTER" -c:v libx264 -preset veryfast -crf 26 -pix_fmt yuv420p "$OUT"
echo "wrote $OUT ($(wc -l <<< "$ANN") labels)"
