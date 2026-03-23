"""Step 34 continuation: 3-baseline evaluation (no-op, random, trained).

Usage:
    python scripts/eval_baselines.py \
        --checkpoint /path/to/policy.pt \
        --output results/step34c_eval.json

Runs 4 envs × 4000 steps (~32 episodes per baseline) with:
  1. No-op (action 0 = wait, auto-retaliate does the fighting)
  2. Random (uniform random from action space)
  3. Trained (greedy argmax from checkpoint)

Outputs a JSON with per-baseline episode metrics.
"""
from __future__ import annotations

import argparse
import json
from datetime import UTC, datetime
from pathlib import Path
from time import perf_counter
from typing import Any

import numpy as np
import torch

from fight_caves_rl.puffer.factory import load_smoke_train_config, make_vecenv
from fight_caves_rl.policies.checkpointing import load_checkpoint_metadata, load_policy_checkpoint
from fight_caves_rl.policies.registry import build_policy_from_metadata
from fight_caves_rl.envs.puffer_encoding import build_policy_observation_space, build_policy_action_space
from fight_caves_rl.envs_fast.fast_spaces import build_fast_observation_space, build_fast_action_space

EVAL_ENV_COUNT = 4
EVAL_STEPS_PER_ENV = 4000
EVAL_TICK_CAP = 512
EVAL_CONFIG_PATH = Path("configs/train/train_fast_v2_mlp64.yaml")


def main() -> None:
    parser = argparse.ArgumentParser(description="3-baseline evaluation for Step 34 continuation.")
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--steps", type=int, default=EVAL_STEPS_PER_ENV)
    args = parser.parse_args()

    config = load_smoke_train_config(EVAL_CONFIG_PATH)
    # Override for eval: 4 envs, tc=512, 1 worker
    config = dict(config)
    config["num_envs"] = EVAL_ENV_COUNT
    env_config = dict(config.get("env", {}))
    env_config["tick_cap"] = EVAL_TICK_CAP
    env_config["subprocess_worker_count"] = 1
    config["env"] = env_config

    results: dict[str, Any] = {
        "created_at": datetime.now(UTC).isoformat(),
        "checkpoint": str(args.checkpoint),
        "steps_per_env": args.steps,
        "env_count": EVAL_ENV_COUNT,
        "tick_cap": EVAL_TICK_CAP,
        "baselines": {},
    }

    # 1. No-op baseline
    print("=== No-op + auto-retaliate baseline ===")
    noop_result = run_baseline(config, args.steps, action_mode="noop")
    results["baselines"]["noop"] = noop_result
    print_baseline_summary("No-op", noop_result)

    # 2. Random baseline
    print("\n=== Random baseline ===")
    random_result = run_baseline(config, args.steps, action_mode="random")
    results["baselines"]["random"] = random_result
    print_baseline_summary("Random", random_result)

    # 3. Trained baseline
    print(f"\n=== Trained baseline ({args.checkpoint}) ===")
    trained_result = run_baseline(
        config, args.steps,
        action_mode="trained",
        checkpoint_path=args.checkpoint,
    )
    results["baselines"]["trained"] = trained_result
    print_baseline_summary("Trained", trained_result)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print(f"\nResults written to {args.output}")


def run_baseline(
    config: dict[str, Any],
    total_steps: int,
    *,
    action_mode: str,
    checkpoint_path: Path | None = None,
) -> dict[str, Any]:
    vecenv = make_vecenv(config, backend="subprocess")
    policy = None
    device = str(config.get("train", {}).get("device", "cuda"))
    if action_mode == "trained" and checkpoint_path is not None:
        metadata = load_checkpoint_metadata(checkpoint_path)
        obs_space = vecenv.single_observation_space
        act_space = vecenv.single_action_space
        policy = build_policy_from_metadata(metadata, obs_space, act_space)
        load_policy_checkpoint(checkpoint_path, policy)
        policy.eval()
        policy = policy.to(device)

    try:
        vecenv.async_reset(seed=42)
        obs, rewards, terminals, truncations, teacher_actions, infos, agent_ids, masks = vecenv.recv()
        episode_returns: list[float] = []
        episode_lengths: list[int] = []
        episode_waves: list[int] = []
        episode_deaths: list[bool] = []

        started = perf_counter()
        for step_idx in range(total_steps):
            if action_mode == "noop":
                actions = np.zeros((vecenv.num_agents, 6), dtype=np.int64)
            elif action_mode == "random":
                actions = np.stack([
                    vecenv.single_action_space.sample()
                    for _ in range(vecenv.num_agents)
                ])
            elif action_mode == "trained" and policy is not None:
                with torch.no_grad():
                    obs_tensor = torch.as_tensor(obs, dtype=torch.float32, device=device)
                    if hasattr(policy, "forward"):
                        output = policy(obs_tensor)
                        if isinstance(output, tuple):
                            logits = output[0]
                        else:
                            logits = output
                    else:
                        logits = policy(obs_tensor)
                    # Multi-discrete: logits are concatenated, split by nvec
                    nvec = vecenv.single_action_space.nvec
                    actions_list = []
                    offset = 0
                    for n in nvec:
                        head_logits = logits[:, offset:offset + n]
                        actions_list.append(torch.argmax(head_logits, dim=-1).cpu().numpy())
                        offset += n
                    actions = np.stack(actions_list, axis=-1)
            else:
                raise ValueError(f"Unknown action_mode: {action_mode!r}")

            vecenv.send(actions)
            obs, rewards, terminals, truncations, teacher_actions, infos, agent_ids, masks = vecenv.recv()

            for i, info in enumerate(infos):
                if info and "episode_return" in info:
                    episode_returns.append(float(info["episode_return"]))
                    episode_lengths.append(int(info.get("episode_length", 0)))
                    episode_waves.append(int(info.get("wave_reached", 1)))
                    episode_deaths.append(info.get("terminal_reason") == "death")

        elapsed = perf_counter() - started
    finally:
        vecenv.close()

    return {
        "action_mode": action_mode,
        "episodes": len(episode_returns),
        "elapsed_seconds": round(elapsed, 2),
        "mean_return": round(float(np.mean(episode_returns)), 4) if episode_returns else 0.0,
        "min_return": round(float(np.min(episode_returns)), 4) if episode_returns else 0.0,
        "max_return": round(float(np.max(episode_returns)), 4) if episode_returns else 0.0,
        "std_return": round(float(np.std(episode_returns)), 4) if episode_returns else 0.0,
        "mean_length": round(float(np.mean(episode_lengths)), 1) if episode_lengths else 0.0,
        "max_wave": int(max(episode_waves)) if episode_waves else 1,
        "deaths": sum(episode_deaths),
        "death_rate": round(sum(episode_deaths) / len(episode_deaths), 3) if episode_deaths else 0.0,
    }


def print_baseline_summary(name: str, result: dict[str, Any]) -> None:
    print(f"  Episodes: {result['episodes']}")
    print(f"  Mean return: {result['mean_return']:+.4f}")
    print(f"  Min return: {result['min_return']:+.4f}")
    print(f"  Max return: {result['max_return']:+.4f}")
    print(f"  Max wave: {result['max_wave']}")
    print(f"  Deaths: {result['deaths']}")
    print(f"  Elapsed: {result['elapsed_seconds']:.1f}s")


if __name__ == "__main__":
    main()
