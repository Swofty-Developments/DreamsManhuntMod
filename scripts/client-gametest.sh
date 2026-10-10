#!/usr/bin/env bash
# Runs the client gametests headless (Xvfb + steam-run FHS libs, Vulkan backend on the NVIDIA card).
# Usage: scripts/client-gametest.sh [test-name|all]   e.g. scripts/client-gametest.sh engine
# Screenshots land in build/run/clientGameTest/screenshots/, logs in build/run/clientGameTest/logs/.
set -euo pipefail
cd "$(dirname "$0")/.."
# One headless run at a time across every worktree; the box needs CPU headroom and the pixel tests need a quiet machine.
exec 9>/tmp/dreammanhunt-headless.lock
if ! flock -n 9; then
	echo "waiting for another headless DreamManhunt run to finish..." >&2
	flock 9
fi
TEST="${1:-all}"
XVFB="$(nix build --no-link --print-out-paths nixpkgs#xvfb-run)/bin/xvfb-run"
exec env -u WAYLAND_DISPLAY ALSOFT_CONF="$PWD/scripts/alsoft-gametest.conf" taskset -c 2-7,10,11,14-31 "$XVFB" -a -s "-screen 0 1280x720x24" \
	steam-run ./gradlew --no-daemon --no-configuration-cache runClientGameTest -PdmTest="$TEST"
