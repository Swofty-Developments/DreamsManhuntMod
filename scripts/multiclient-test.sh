#!/usr/bin/env bash
# Multi-client savestate test: a dedicated server with the mod plus five headless real clients
# (runner, two hunters, a hunter joining after the save, and a client with the mod's networking
# disabled), driven by the test-only MultiClientDriver entrypoint. See scripts/multiclient/run.py.
# Usage: scripts/multiclient-test.sh        Results: build/multiclient/run-*/harness.log
set -euo pipefail
cd "$(dirname "$0")/.."
# At most two headless runs at a time across every worktree; the box needs CPU headroom and the pixel tests need a quiet machine.
DM_CPUS="${DM_CPUS:-2-7,10,11,14-19}"
until { exec 9>/tmp/dreammanhunt-headless-1.lock && flock -n 9; } || { exec 9>/tmp/dreammanhunt-headless-2.lock && flock -n 9; }; do
	echo "waiting for a free headless DreamManhunt slot..." >&2
	sleep 5
done
taskset -c "$DM_CPUS" ./gradlew --no-configuration-cache --init-script scripts/multiclient/launch-info.gradle \
	multiclientLaunchInfo -q
exec taskset -c "$DM_CPUS" python3 scripts/multiclient/run.py "$@"
