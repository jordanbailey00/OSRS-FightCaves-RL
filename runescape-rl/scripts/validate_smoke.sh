#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/bootstrap.sh" >/dev/null

log_root="$FIGHT_CAVES_RUNTIME_ROOT/reports/validation"
mkdir -p "$log_root"

echo "[1/3] demo-env module load through training-env/sim composite build"
(
  cd "$SOURCE_ROOT/training-env/sim"
  ./gradlew --no-daemon :fight-caves-demo-lite:tasks --all
) >"$log_root/demo-env.tasks.stdout.log" 2>"$log_root/demo-env.tasks.stderr.log"

echo "[2/3] training-env sim module load"
(
  cd "$SOURCE_ROOT/training-env/sim"
  ./gradlew --no-daemon :game:tasks --all
) >"$log_root/training-env.sim.test.stdout.log" 2>"$log_root/training-env.sim.test.stderr.log"

echo "[3/3] training-env rl backend selector dry-run"
(
  cd "$SOURCE_ROOT/training-env/rl"
  python3 scripts/run_demo_backend.py --dry-run
) >"$log_root/training-env.rl.backend_dry_run.stdout.log" 2>"$log_root/training-env.rl.backend_dry_run.stderr.log"

# Keep source root clean after smoke compilation/configuration checks.
rm -rf "$SOURCE_ROOT/demo-env/.gradle" "$SOURCE_ROOT/demo-env/build" "$SOURCE_ROOT/demo-env/session-logs"
rm -rf \
  "$SOURCE_ROOT/training-env/sim/.gradle" \
  "$SOURCE_ROOT/training-env/sim/.kotlin" \
  "$SOURCE_ROOT/training-env/sim/.run" \
  "$SOURCE_ROOT/training-env/sim/buffer/build" \
  "$SOURCE_ROOT/training-env/sim/build-logic/build" \
  "$SOURCE_ROOT/training-env/sim/buildSrc/build" \
  "$SOURCE_ROOT/training-env/sim/cache/build" \
  "$SOURCE_ROOT/training-env/sim/config/build" \
  "$SOURCE_ROOT/training-env/sim/engine/build" \
  "$SOURCE_ROOT/training-env/sim/game/build" \
  "$SOURCE_ROOT/training-env/sim/network/build" \
  "$SOURCE_ROOT/training-env/sim/types/build"
rm -rf \
  "$SOURCE_ROOT/training-env/sim/build-logic/.gradle" \
  "$SOURCE_ROOT/training-env/sim/build-logic/.kotlin" \
  "$SOURCE_ROOT/training-env/sim/buildSrc/.gradle" \
  "$SOURCE_ROOT/training-env/sim/buildSrc/.kotlin"

echo "Smoke validation passed"
echo "Logs: $log_root"
