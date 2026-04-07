# Serverless Setup

## Current Flow

Clone `v2codex`, run one bootstrap script, then run training.

## What The Bootstrap Script Does

[serverless_bootstrap.sh](/home/joe/projects/runescape-rl/codex3/runescape-rl/serverless_bootstrap.sh) will:

- install system packages
- create `.venv`
- install Python deps
- install editable `pufferlib_4`
- log into W&B if `WANDB_API_KEY` is set
- download the `v18.3` warm-start checkpoint if `WARMSTART_URL` is set
- build the local Fight Caves backend

## Pod Commands

Clone:

```bash
git clone --branch v2codex git@github.com:jordanbailey00/runescape-rl.git
cd runescape-rl/runescape-rl
```

Bootstrap:

```bash
WANDB_API_KEY=<wandb-api-key> WARMSTART_URL=<checkpoint-url> bash serverless_bootstrap.sh
```

Run `v18.3` sweep:

```bash
bash sweep_v18_3.sh
```

## Notes

- If the checkpoint is already present, `WARMSTART_URL` is not needed.
- If you do not want W&B login during setup, omit `WANDB_API_KEY`.
- The expected warm-start checkpoint path is:
  - `pufferlib_4/checkpoints/fight_caves/xm6i52ta/0000005977931776.bin`
