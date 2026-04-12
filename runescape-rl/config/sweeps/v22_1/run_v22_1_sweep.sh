#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
TRAIN_SH="$ROOT_DIR/train.sh"
WARM_START_CKPT="/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/u58coupx/0000001311768576.bin"
WANDB_GROUP="${WANDB_GROUP:-v24_s_redo}"

TEMP_CONFIGS=()

cleanup() {
    if [ "${#TEMP_CONFIGS[@]}" -gt 0 ]; then
        rm -f "${TEMP_CONFIGS[@]}"
    fi
}

trap cleanup EXIT

materialize_config() {
    local base_config="$1"
    local label="$2"

    if [ -z "${TIMESTEPS_OVERRIDE:-}" ]; then
        printf '%s\n' "$base_config"
        return
    fi

    local tmp_config
    tmp_config="$(mktemp "${TMPDIR:-/tmp}/fc_${label}_XXXXXX.ini")"
    awk -v steps="$TIMESTEPS_OVERRIDE" '
        BEGIN { in_train = 0; replaced = 0 }
        /^\[train\]/ { in_train = 1; print; next }
        /^\[/ { in_train = 0 }
        in_train && $1 == "total_timesteps" {
            print "total_timesteps = " steps
            replaced = 1
            next
        }
        { print }
        END {
            if (!replaced) {
                exit 2
            }
        }
    ' "$base_config" > "$tmp_config"
    TEMP_CONFIGS+=("$tmp_config")
    printf '%s\n' "$tmp_config"
}

usage() {
    cat <<'EOF'
Usage:
  bash run_v22_1_sweep.sh [--from LABEL] [--timesteps STEPS] [--dry-run] [--no-wandb] [--list]

Options:
  --from LABEL   Resume from the named job label.
  --timesteps N  Override train.total_timesteps for every job in the matrix.
  --dry-run      Print the jobs that would run without launching training.
  --no-wandb     Pass through --no-wandb to train.sh.
  --list         Print the ordered job labels and exit.

Ordered job labels:
  warm_control
  cold_control
  warm_no_low_prayer
  cold_no_low_prayer
  warm_no_reward_retune
  cold_no_reward_retune
  warm_no_reward_retune_no_low_prayer
  cold_no_reward_retune_no_low_prayer
EOF
}

LABELS=(
    warm_control
    cold_control
    warm_no_low_prayer
    cold_no_low_prayer
    warm_no_reward_retune
    cold_no_reward_retune
    warm_no_reward_retune_no_low_prayer
    cold_no_reward_retune_no_low_prayer
)

CONFIGS=(
    "$SCRIPT_DIR/fight_caves_v22_1_control.ini"
    "$SCRIPT_DIR/fight_caves_v22_1_control_cold.ini"
    "$SCRIPT_DIR/fight_caves_v22_1_no_low_prayer.ini"
    "$SCRIPT_DIR/fight_caves_v22_1_no_low_prayer_cold.ini"
    "$SCRIPT_DIR/fight_caves_v22_1_no_reward_retune.ini"
    "$SCRIPT_DIR/fight_caves_v22_1_no_reward_retune_cold.ini"
    "$SCRIPT_DIR/fight_caves_v22_1_no_reward_retune_no_low_prayer.ini"
    "$SCRIPT_DIR/fight_caves_v22_1_no_reward_retune_no_low_prayer_cold.ini"
)

WARM_FLAGS=(
    1
    0
    1
    0
    1
    0
    1
    0
)

FROM_LABEL=""
TIMESTEPS_OVERRIDE="${SWEEP_TOTAL_TIMESTEPS:-}"
DRY_RUN=0
NO_WANDB=0
LIST_ONLY=0

while [ "$#" -gt 0 ]; do
    case "$1" in
        --from)
            [ "$#" -ge 2 ] || { echo "Missing value for --from" >&2; exit 1; }
            FROM_LABEL="$2"
            shift 2
            ;;
        --timesteps)
            [ "$#" -ge 2 ] || { echo "Missing value for --timesteps" >&2; exit 1; }
            TIMESTEPS_OVERRIDE="$2"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=1
            shift
            ;;
        --no-wandb)
            NO_WANDB=1
            shift
            ;;
        --list)
            LIST_ONLY=1
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

if [ ! -f "$TRAIN_SH" ]; then
    echo "train.sh not found at $TRAIN_SH" >&2
    exit 1
fi

if [ "$LIST_ONLY" -eq 1 ]; then
    printf '%s\n' "${LABELS[@]}"
    exit 0
fi

start_idx=0
if [ -n "$FROM_LABEL" ]; then
    found=0
    for i in "${!LABELS[@]}"; do
        if [ "${LABELS[$i]}" = "$FROM_LABEL" ]; then
            start_idx="$i"
            found=1
            break
        fi
    done
    if [ "$found" -eq 0 ]; then
        echo "Unknown --from label: $FROM_LABEL" >&2
        printf 'Valid labels:\n' >&2
        printf '  %s\n' "${LABELS[@]}" >&2
        exit 1
    fi
fi

for i in "${!LABELS[@]}"; do
    if [ "$i" -lt "$start_idx" ]; then
        continue
    fi

    label="${LABELS[$i]}"
    base_config="${CONFIGS[$i]}"
    warm="${WARM_FLAGS[$i]}"
    config="$base_config"

    if [ "$DRY_RUN" -ne 1 ]; then
        config="$(materialize_config "$base_config" "$label")"
    fi

    echo
    echo "=== [$((i + 1))/${#LABELS[@]}] $label ==="
    echo "config: $base_config"
    if [ -n "$TIMESTEPS_OVERRIDE" ]; then
        echo "timesteps_override: $TIMESTEPS_OVERRIDE"
    fi
    echo "wandb_group: $WANDB_GROUP"
    echo "wandb_tag: $label"
    if [ "$warm" -eq 1 ]; then
        echo "warm_start: $WARM_START_CKPT"
    else
        echo "warm_start: none"
    fi

    cmd=(bash "$TRAIN_SH" --wandb-group "$WANDB_GROUP" --tag "$label")
    if [ "$NO_WANDB" -eq 1 ]; then
        cmd+=(--no-wandb)
    fi

    if [ "$DRY_RUN" -eq 1 ]; then
        if [ "$warm" -eq 1 ]; then
            printf 'CONFIG_PATH=%q LOAD_MODEL_PATH=%q ' "$base_config" "$WARM_START_CKPT"
        else
            printf 'CONFIG_PATH=%q ' "$base_config"
        fi
        if [ -n "$TIMESTEPS_OVERRIDE" ]; then
            printf 'SWEEP_TOTAL_TIMESTEPS=%q ' "$TIMESTEPS_OVERRIDE"
        fi
        if [ "$i" -eq "$start_idx" ]; then
            printf 'FORCE_BACKEND_REBUILD=1 '
        fi
        printf '%q ' "${cmd[@]}"
        printf '\n'
        continue
    fi

    if [ "$warm" -eq 1 ]; then
        if [ "$i" -eq "$start_idx" ]; then
            CONFIG_PATH="$config" LOAD_MODEL_PATH="$WARM_START_CKPT" FORCE_BACKEND_REBUILD=1 "${cmd[@]}"
        else
            CONFIG_PATH="$config" LOAD_MODEL_PATH="$WARM_START_CKPT" "${cmd[@]}"
        fi
    else
        if [ "$i" -eq "$start_idx" ]; then
            CONFIG_PATH="$config" FORCE_BACKEND_REBUILD=1 "${cmd[@]}"
        else
            CONFIG_PATH="$config" "${cmd[@]}"
        fi
    fi
done
