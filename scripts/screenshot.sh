#!/usr/bin/env bash
# Capture two screenshots of the running nop window — one of the diff view and one of
# the README rendered-markdown preview, in opposite themes — and embed them in the README.
#
# Designed to be invoked from inside nop (via the ▶ launcher). It targets the running
# nop process so we get a real picture of the current state, instead of spawning a
# fresh instance that wouldn't have the user's open tabs.
#
# Output is quantised to a 256-colour palette before saving, which trims the PNGs by
# ~3x with no visible difference vs. the truecolor capture.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SHOT_DIR="$ROOT_DIR/docs/screenshots"
README="$ROOT_DIR/README.md"
DISPLAY_SPEC="${DISPLAY:-:0}"
README_MARKER="<!-- screenshot -->"

mkdir -p "$SHOT_DIR"

for cmd in wmctrl xdotool xwininfo import convert; do
    if ! command -v "$cmd" >/dev/null; then
        echo "missing required tool: $cmd" >&2
        exit 1
    fi
done

# Pick the nop window. The shell-launched run is the one that spawned us, but we don't
# rely on that — we just take whatever window the user can see right now.
wid=$(DISPLAY="$DISPLAY_SPEC" wmctrl -l | awk '/nop — / {print $1; exit}')
if [ -z "$wid" ]; then
    echo "no nop window found via wmctrl" >&2
    exit 2
fi

DISPLAY="$DISPLAY_SPEC" xdotool windowactivate --sync "$wid"

# xdotool's getwindowgeometry returns the WM-hinted origin, which on xfwm4 doesn't match
# the actual on-screen position of the client area. xwininfo reads from the X server
# directly and reports the client's absolute coordinates, which is what `import -window`
# captures and what `xdotool mousemove` consumes — so everything stays in one coord
# space when we drive it from xwininfo.
read X Y WIDTH HEIGHT < <(DISPLAY="$DISPLAY_SPEC" xwininfo -id "$wid" | awk '
    /Absolute upper-left X:/ {x=$NF}
    /Absolute upper-left Y:/ {y=$NF}
    /Width:/  {w=$NF}
    /Height:/ {h=$NF}
    END {print x, y, w, h}
')

# Park the cursor off-window between actions so the pointer doesn't leak into the shot.
park_cursor() {
    DISPLAY="$DISPLAY_SPEC" xdotool mousemove "$X" "$Y"
}

# The theme toggle floats at the bottom-right of the window, with ~8dp padding inside
# the IconButton itself. ~18px in from each edge lands on its hit box at default scale.
theme_x=$((X + WIDTH - 18))
theme_y=$((Y + HEIGHT - 18))

click_theme_toggle() {
    DISPLAY="$DISPLAY_SPEC" xdotool windowactivate --sync "$wid"
    # Move and click as separate calls: chaining `mousemove X Y click 1` in one invocation
    # races with xfwm4 under our test harness — the click sometimes fires before the move
    # has settled, missing the 16dp IconButton hitbox.
    DISPLAY="$DISPLAY_SPEC" xdotool mousemove "$theme_x" "$theme_y"
    sleep 0.2
    DISPLAY="$DISPLAY_SPEC" xdotool click 1
    park_cursor
    # The IntUi theme swap involves a recomposition + style cache rebuild — give Skia a
    # few frames to settle so we don't snap a half-repainted intermediate.
    sleep 0.8
}

# Capture, palette-quantise, and write to $1.
capture_to() {
    local out="$1"
    local raw
    raw="$(mktemp --suffix=.png)"
    DISPLAY="$DISPLAY_SPEC" import -window "$wid" "$raw"
    # 256 colours + max zlib compression. Strip metadata; +dither keeps text crisp.
    convert "$raw" -strip -colors 256 -dither None \
        -define png:compression-level=9 -define png:compression-filter=5 "$out"
    rm -f "$raw"
}

ts=$(date +%Y%m%d-%H%M%S)
diff_out="$SHOT_DIR/${ts}-diff.png"
preview_out="$SHOT_DIR/${ts}-preview.png"

# --- Screenshot 1: diff view ---
# Synthesize a click on a row of the bottom commit panel. The layout uses a
# 0.55 vertical split with the commit panel below — see App.kt's rememberSplitLayoutState.
# Inside the panel: header row + message box (64dp) + button strip + paddings stack up
# to ~126px before the change list. Rows are ~28px tall.
#
# We target the THIRD change row instead of the first because the first two are often
# the screenshots themselves (this script's own outputs are versioned, and overwriting
# them shows up as a change immediately) and a PNG diff renders as "Loading diff…" in
# nop's text-only diff view. Skipping to row 3 lands on something diffable.
panel_top=$((Y + HEIGHT * 55 / 100))
diff_x=$((X + WIDTH * 30 / 100))
diff_y=$((panel_top + 126 + 56))
DISPLAY="$DISPLAY_SPEC" xdotool mousemove "$diff_x" "$diff_y" click 1
park_cursor
# Give the diff machinery a moment to compute + lay out — PNGs are quick to bail and
# Kotlin diffs over a few hundred lines take a beat.
sleep 1.0
capture_to "$diff_out"

# --- Theme toggle so the second shot lands in the opposite mode ---
click_theme_toggle

# --- Screenshot 2: README rendered-markdown preview ---
# Use nop's own double-shift file-search dialog to jump straight to README.md. This
# avoids brittle pixel arithmetic against the tree, which shifts when ancestors get
# auto-expanded by the diff click above.
#
# Pointedly NOT using xdotool's `--window` flag here: that path goes via XSendEvent
# and Compose's key listeners don't fire on synthetic events. Without --window the
# focused window receives the keys naturally.
DISPLAY="$DISPLAY_SPEC" xdotool windowactivate --sync "$wid"
DISPLAY="$DISPLAY_SPEC" xdotool key shift
sleep 0.05
DISPLAY="$DISPLAY_SPEC" xdotool key shift
sleep 0.3
DISPLAY="$DISPLAY_SPEC" xdotool type --delay 20 "README"
sleep 0.3
DISPLAY="$DISPLAY_SPEC" xdotool key Return
park_cursor
sleep 0.6
capture_to "$preview_out"

# Restore the user's original theme so the script doesn't surprise them later.
click_theme_toggle

# Symlinkish "latest" pointers — copies, since GitHub's markdown serves the link target
# text as the blob for symlinks rather than following them.
cp -f "$diff_out" "$SHOT_DIR/latest-diff.png"
cp -f "$preview_out" "$SHOT_DIR/latest-preview.png"

diff_size=$(stat -c %s "$diff_out")
preview_size=$(stat -c %s "$preview_out")
echo "wrote $diff_out (${diff_size} bytes)"
echo "wrote $preview_out (${preview_size} bytes)"

# Insert / replace a screenshot block in the README so the latest captures show up
# inline. The marker pair lets us update in-place without growing the file each run.
block="$README_MARKER
![Diff view](docs/screenshots/latest-diff.png)
![README preview](docs/screenshots/latest-preview.png)
*Captured $(date '+%Y-%m-%d %H:%M:%S') — light & dark mode toggled via the header button*
$README_MARKER"

if grep -q "$README_MARKER" "$README"; then
    awk -v block="$block" -v marker="$README_MARKER" '
        $0 ~ marker && !seen { print block; seen = 1; in_block = 1; next }
        in_block && $0 ~ marker { in_block = 0; next }
        !in_block { print }
    ' "$README" > "$README.tmp"
    mv "$README.tmp" "$README"
else
    printf '\n%s\n' "$block" >> "$README"
fi

echo "README updated"
