#!/usr/bin/env bash
# Runs the client gametests headless (Xvfb + steam-run FHS libs, Vulkan backend on the NVIDIA card).
# Usage: scripts/client-gametest.sh [test-name|all]   e.g. scripts/client-gametest.sh engine
# Screenshots land in build/run/clientGameTest/screenshots/, logs in build/run/clientGameTest/logs/.
set -euo pipefail
cd "$(dirname "$0")/.."
TEST="${1:-all}"
XVFB="$(nix build --no-link --print-out-paths nixpkgs#xvfb-run)/bin/xvfb-run"
exec env -u WAYLAND_DISPLAY taskset -c 2-7,10,11,14-31 "$XVFB" -a -s "-screen 0 1280x720x24" \
	steam-run ./gradlew --no-daemon --no-configuration-cache runClientGameTest -PdmTest="$TEST"
