#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/bootstrap.sh" >/dev/null

log_root="$FIGHT_CAVES_RUNTIME_ROOT/reports/validation"
mkdir -p "$log_root"

echo "[1/3] headed-env module load through headless-env composite build"
(
  cd "$SOURCE_ROOT/src/headless-env"
  ./gradlew --no-daemon :fight-caves-demo-lite:tasks --all
) >"$log_root/headed-env.tasks.stdout.log" 2>"$log_root/headed-env.tasks.stderr.log"

echo "[2/3] headless-env sim module load"
(
  cd "$SOURCE_ROOT/src/headless-env"
  ./gradlew --no-daemon :game:tasks --all
) >"$log_root/headless-env.sim.test.stdout.log" 2>"$log_root/headless-env.sim.test.stderr.log"

echo "[3/3] rl backend selector dry-run"
(
  cd "$SOURCE_ROOT/src/rl"
  python3 scripts/run_demo_backend.py --dry-run
) >"$log_root/rl.backend_dry_run.stdout.log" 2>"$log_root/rl.backend_dry_run.stderr.log"

# Keep source root clean after smoke compilation/configuration checks.
rm -rf "$SOURCE_ROOT/src/headed-env/.gradle" "$SOURCE_ROOT/src/headed-env/build" "$SOURCE_ROOT/src/headed-env/session-logs"
rm -rf \
  "$SOURCE_ROOT/src/headless-env/.gradle" \
  "$SOURCE_ROOT/src/headless-env/.kotlin" \
  "$SOURCE_ROOT/src/headless-env/.run" \
  "$SOURCE_ROOT/src/headless-env/buffer/build" \
  "$SOURCE_ROOT/src/headless-env/build-logic/build" \
  "$SOURCE_ROOT/src/headless-env/buildSrc/build" \
  "$SOURCE_ROOT/src/headless-env/cache/build" \
  "$SOURCE_ROOT/src/headless-env/config/build" \
  "$SOURCE_ROOT/src/headless-env/engine/build" \
  "$SOURCE_ROOT/src/headless-env/game/build" \
  "$SOURCE_ROOT/src/headless-env/network/build" \
  "$SOURCE_ROOT/src/headless-env/types/build"
rm -rf \
  "$SOURCE_ROOT/src/headless-env/build-logic/.gradle" \
  "$SOURCE_ROOT/src/headless-env/build-logic/.kotlin" \
  "$SOURCE_ROOT/src/headless-env/buildSrc/.gradle" \
  "$SOURCE_ROOT/src/headless-env/buildSrc/.kotlin"

echo "Smoke validation passed"
echo "Logs: $log_root"
