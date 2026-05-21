#!/usr/bin/env bash
# Auto-rebuild loop. Run once in a terminal; leave it. Saves under src/ trigger a Gradle
# continuous-build pass that rewrites build/compose/binaries/main/app/nop/bin/nop, so the
# menu launcher's next click loads the latest code without any manual step.
#
# Uses Gradle continuous mode (--continuous), which natively watches the inputs of the
# requested task chain. installDesktopEntry has explicit inputs/outputs declared in
# build.gradle.kts so up-to-date checks work cleanly and a no-op edit is a no-op build.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_DIR"

notify() {
    if command -v notify-send >/dev/null 2>&1; then
        notify-send -i utilities-terminal "nop" "$1" >/dev/null 2>&1 || true
    fi
}

echo "Starting nop auto-rebuild loop in $REPO_DIR"
echo "Watching src/ via Gradle --continuous. Ctrl-C to stop."
echo

notify "watch started"
trap 'notify "watch stopped"; exit 0' INT TERM

# --console=plain keeps the output linear (no live-updating block) so we can grep it,
# but Gradle still prints task results between builds.
./gradlew --continuous --console=plain installDesktopEntry 2>&1 | while IFS= read -r line; do
    echo "$line"
    case "$line" in
        *"BUILD SUCCESSFUL"*) notify "rebuilt — menu launcher now has your latest changes" ;;
        *"BUILD FAILED"*) notify "build FAILED — menu launcher is stale" ;;
    esac
done
