#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
TRAIN_SH="$ROOT_DIR/train.sh"
WARM_START_CKPT="/home/joe/projects/runescape-rl/codex3/pufferlib_4/checkpoints/fight_caves/u58coupx/0000001311768576.bin"

usage() {
    cat <<'EOF'
Usage:
  bash run_v22_1_sweep.sh [--from LABEL] [--dry-run] [--no-wandb] [--list]

Options:
  --from LABEL   Resume from the named job label.
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
    config="${CONFIGS[$i]}"
    warm="${WARM_FLAGS[$i]}"

    echo
    echo "=== [$((i + 1))/${#LABELS[@]}] $label ==="
    echo "config: $config"
    if [ "$warm" -eq 1 ]; then
        echo "warm_start: $WARM_START_CKPT"
    else
        echo "warm_start: none"
    fi

    cmd=(bash "$TRAIN_SH")
    if [ "$NO_WANDB" -eq 1 ]; then
        cmd+=(--no-wandb)
    fi

    if [ "$DRY_RUN" -eq 1 ]; then
        if [ "$warm" -eq 1 ]; then
            printf 'CONFIG_PATH=%q LOAD_MODEL_PATH=%q ' "$config" "$WARM_START_CKPT"
        else
            printf 'CONFIG_PATH=%q ' "$config"
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
