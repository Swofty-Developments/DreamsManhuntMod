#!/usr/bin/env bash
# Multi-client savestate test: a dedicated server with the mod plus five headless real clients
# (runner, two hunters, a hunter joining after the save, and a client with the mod's networking
# disabled), driven by the test-only MultiClientDriver entrypoint. See scripts/multiclient/run.py.
# Usage: scripts/multiclient-test.sh        Results: build/multiclient/run-*/harness.log
set -euo pipefail
cd "$(dirname "$0")/.."
# One headless run at a time across every worktree; the box needs CPU headroom and the pixel tests need a quiet machine.
exec 9>/tmp/dreammanhunt-headless.lock
if ! flock -n 9; then
	echo "waiting for another headless DreamManhunt run to finish..." >&2
	flock 9
fi
taskset -c 2-7,10,11,14-31 ./gradlew --no-configuration-cache --init-script scripts/multiclient/launch-info.gradle \
	multiclientLaunchInfo -q
exec taskset -c 2-7,10,11,14-31 python3 scripts/multiclient/run.py "$@"
