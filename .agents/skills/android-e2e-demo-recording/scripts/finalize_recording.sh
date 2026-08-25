#!/usr/bin/env bash
# Turn the screen recorder's raw chunks into ONE 1x mp4 plus a contact sheet.
# Never ship the recorder's own *-edited.mp4: it is time lapsed (200s -> 18s).
#
# Usage: finalize_recording.sh <screencast-dir> [output.mp4]
set -euo pipefail

DIR="${1:?usage: finalize_recording.sh <screencast-dir> [output.mp4]}"
OUT="${2:-$PWD/take_1x.mp4}"

cd "$DIR"
mapfile -t RAWS < <(ls -- *-raw-*.mkv 2>/dev/null | sort)
[ "${#RAWS[@]}" -gt 0 ] || { echo "no *-raw-*.mkv chunks in $DIR" >&2; exit 1; }

# The chunks are one continuous capture, split only by the recorder.
printf "file '%s'\n" "${RAWS[@]}" > /tmp/raws.txt
ffmpeg -y -f concat -safe 0 -i /tmp/raws.txt \
    -c:v libx264 -preset veryfast -crf 26 -pix_fmt yuv420p "$OUT"

SHEET="${OUT%.mp4}_sheet.png"
ffmpeg -y -i "$OUT" -vf "fps=1/12,scale=320:-1,tile=6x3" -frames:v 1 "$SHEET"

echo "wrote $OUT"
echo "wrote $SHEET  (skim it: every phase of the run should be visible)"
ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT"
