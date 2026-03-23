"""Standalone PPO benchmark engine for comparing stock vs RS-style training.

This implements a minimal PPO training loop that mirrors PufferLib's PuffeRL
without depending on its internal config machinery. It isolates the comparison
to the sampling/entropy method while keeping everything else identical.

Usage:
    results = run_benchmark(env, policy, config, sample_fn=sample_logits_gumbel)
"""
from __future__ import annotations

import time
from collections import defaultdict
from dataclasses import dataclass, field
from typing import Callable

import numpy as np
import torch
import pufferlib
import pufferlib.pytorch
import pufferlib.vector

try:
    from pufferlib import _C
    ADVANTAGE_CUDA = True
except ImportError:
    ADVANTAGE_CUDA = False

from .gumbel_sample import sample_logits_gumbel


@dataclass
class BenchmarkConfig:
    """Training config for benchmarking. Mirrors PuffeRL defaults."""
    num_envs: int = 4096
    batch_size: int = 0  # 0 = auto (total_agents * bptt_horizon)
    bptt_horizon: int = 16
    total_timesteps: int = 100_000
    learning_rate: float = 0.015
    gamma: float = 0.995
    gae_lambda: float = 0.90
    clip_coef: float = 0.2
    vf_coef: float = 2.0
    vf_clip_coef: float = 0.2
    ent_coef: float = 0.001
    max_grad_norm: float = 1.5
    prio_alpha: float = 0.0
    prio_beta0: float = 1.0
    minibatch_size: int = 8192
    device: str = "cuda"
    seed: int = 42
    warmup_steps: int = 0
    benchmark_duration_s: float = 0  # 0 = use total_timesteps; >0 = time-based


@dataclass
class BenchmarkResult:
    """Results from a benchmark run."""
    label: str = ""
    total_steps: int = 0
    wall_seconds: float = 0.0
    sps: float = 0.0
    eval_seconds: float = 0.0
    train_seconds: float = 0.0
    eval_sps: float = 0.0
    train_sps: float = 0.0
    epochs: int = 0
    device: str = ""
    num_envs: int = 0
    extra: dict = field(default_factory=dict)


def compute_gae(
    values: torch.Tensor,
    rewards: torch.Tensor,
    terminals: torch.Tensor,
    gamma: float,
    gae_lambda: float,
) -> torch.Tensor:
    """Compute GAE advantages. Shape: (segments, horizon)."""
    segments, horizon = values.shape
    advantages = torch.zeros_like(values)
    last_gae = torch.zeros(segments, device=values.device)
    for t in reversed(range(horizon)):
        if t == horizon - 1:
            next_value = values[:, t]
        else:
            next_value = values[:, t + 1]
        delta = rewards[:, t] + gamma * next_value * (1 - terminals[:, t]) - values[:, t]
        last_gae = delta + gamma * gae_lambda * (1 - terminals[:, t]) * last_gae
        advantages[:, t] = last_gae
    return advantages


def run_env_only_benchmark(
    env_creator: Callable,
    env_kwargs: dict,
    num_envs: int = 4096,
    duration_s: float = 10.0,
    action_cache_size: int = 1024,
) -> BenchmarkResult:
    """Benchmark raw env stepping speed (no training)."""
    env = env_creator(num_envs=num_envs, **env_kwargs)
    env.reset()

    # Build action cache
    atn_space = env.single_action_space
    if hasattr(atn_space, 'nvec'):
        actions = np.column_stack([
            np.random.randint(0, n, (action_cache_size, env.num_agents))
            for n in atn_space.nvec
        ]).reshape(action_cache_size, env.num_agents, -1)
    elif hasattr(atn_space, 'n'):
        actions = np.random.randint(0, atn_space.n, (action_cache_size, env.num_agents))
    else:
        actions = np.random.uniform(-1, 1, (action_cache_size, env.num_agents, *atn_space.shape)).astype(np.float32)

    tick = 0
    start = time.time()
    while time.time() - start < duration_s:
        env.step(actions[tick % action_cache_size])
        tick += 1
    elapsed = time.time() - start
    sps = env.num_agents * tick / elapsed
    env.close()

    return BenchmarkResult(
        label="env_only",
        total_steps=env.num_agents * tick,
        wall_seconds=elapsed,
        sps=sps,
        device="cpu",
        num_envs=num_envs,
    )


def run_training_benchmark(
    env_creator: Callable,
    env_kwargs: dict,
    policy_creator: Callable,
    config: BenchmarkConfig,
    sample_fn: Callable = None,
    label: str = "training",
    num_vec_workers: int = 1,
    num_vec_buffers: int = 1,
) -> BenchmarkResult:
    """Run a full PPO training benchmark with the given sampling function.

    Args:
        env_creator: Callable that creates a PufferEnv.
        env_kwargs: Kwargs for the env creator (excluding num_envs).
        policy_creator: Callable(vecenv) -> nn.Module policy on device.
        config: Benchmark configuration.
        sample_fn: Sampling function. None = stock pufferlib.pytorch.sample_logits.
        label: Label for this benchmark run.
        num_vec_workers: Number of vectorization workers.
        num_vec_buffers: Number of vectorization buffers.
    """
    if sample_fn is None:
        sample_fn = pufferlib.pytorch.sample_logits

    device = config.device
    if device == "cuda" and not torch.cuda.is_available():
        device = "cpu"

    # Create vectorized env
    vecenv = pufferlib.vector.make(
        env_creator,
        num_envs=num_vec_workers,
        num_workers=num_vec_workers,
        batch_size=num_vec_buffers,
        backend=pufferlib.vector.Multiprocessing,
        env_kwargs={"num_envs": config.num_envs, **env_kwargs},
    )
    vecenv.async_reset(config.seed)

    obs_space = vecenv.single_observation_space
    atn_space = vecenv.single_action_space
    total_agents = vecenv.num_agents

    # Compute sizes
    horizon = config.bptt_horizon
    batch_size = config.batch_size if config.batch_size > 0 else total_agents * horizon
    segments = batch_size // horizon
    if total_agents > segments:
        raise ValueError(f"total_agents {total_agents} > segments {segments}")

    minibatch_size = min(config.minibatch_size, batch_size)
    minibatch_segments = minibatch_size // horizon
    num_minibatches = max(1, batch_size // minibatch_size)

    # Create policy
    policy = policy_creator(vecenv).to(device)

    # Optimizer (match PuffeRL: Muon if available, else Adam)
    try:
        from pufferlib.muon import Muon
        optimizer = Muon(
            policy.parameters(),
            lr=config.learning_rate,
            momentum=0.95,
            eps=1e-12,
        )
    except ImportError:
        optimizer = torch.optim.Adam(
            policy.parameters(),
            lr=config.learning_rate,
            eps=1e-5,
        )

    # Experience buffers
    observations = torch.zeros(
        segments, horizon, *obs_space.shape,
        dtype=pufferlib.pytorch.numpy_to_torch_dtype_dict[obs_space.dtype],
        device=device,
    )
    actions = torch.zeros(
        segments, horizon, *atn_space.shape,
        dtype=pufferlib.pytorch.numpy_to_torch_dtype_dict[atn_space.dtype],
        device=device,
    )
    values = torch.zeros(segments, horizon, device=device)
    logprobs = torch.zeros(segments, horizon, device=device)
    rewards = torch.zeros(segments, horizon, device=device)
    terminals = torch.zeros(segments, horizon, device=device)

    ep_lengths = torch.zeros(total_agents, device=device, dtype=torch.int32)
    ep_indices = torch.arange(total_agents, device=device, dtype=torch.int32)
    free_idx = total_agents

    # Training loop
    global_step = 0
    epoch = 0
    eval_total_s = 0.0
    train_total_s = 0.0

    total_timesteps = config.total_timesteps
    use_time_limit = config.benchmark_duration_s > 0
    benchmark_start = time.time()

    def should_continue():
        if use_time_limit:
            return (time.time() - benchmark_start) < config.benchmark_duration_s
        return global_step < total_timesteps

    while should_continue():
        # === EVALUATE ===
        eval_start = time.time()
        full_rows = 0
        while full_rows < segments:
            o, r, d, t, info, env_id, mask = vecenv.recv()
            env_id = slice(env_id[0], env_id[-1] + 1)
            global_step += int(mask.sum())

            o = torch.as_tensor(o)
            o_device = o.to(device)
            r = torch.as_tensor(r).to(device)
            d = torch.as_tensor(d).to(device)

            with torch.no_grad():
                result = policy.forward_eval(o_device, None)
                logits, value = result[0], result[1]
                action, logprob, _ = sample_fn(logits)
                r = torch.clamp(r, -1, 1)

            with torch.no_grad():
                l = ep_lengths[env_id.start].item()
                batch_rows = slice(
                    ep_indices[env_id.start].item(),
                    1 + ep_indices[env_id.stop - 1].item(),
                )
                observations[batch_rows, l] = o_device
                actions[batch_rows, l] = action
                logprobs[batch_rows, l] = logprob
                rewards[batch_rows, l] = r
                terminals[batch_rows, l] = d.float()
                values[batch_rows, l] = value.flatten()

                ep_lengths[env_id] += 1
                if l + 1 >= horizon:
                    num_full = env_id.stop - env_id.start
                    ep_indices[env_id] = free_idx + torch.arange(
                        num_full, device=device
                    ).int()
                    ep_lengths[env_id] = 0
                    free_idx += num_full
                    full_rows += num_full

                action = action.cpu().numpy()

            vecenv.send(action)

        free_idx = total_agents
        ep_indices = torch.arange(total_agents, device=device, dtype=torch.int32)
        ep_lengths.zero_()
        eval_total_s += time.time() - eval_start

        # === TRAIN ===
        train_start = time.time()
        a = config.prio_alpha
        clip_coef = config.clip_coef
        vf_clip = config.vf_clip_coef

        for mb in range(num_minibatches):
            # Compute advantages
            advantages = compute_gae(values, rewards, terminals, config.gamma, config.gae_lambda)

            # Minibatch selection
            if a == 0:
                idx = torch.randperm(segments, device=device)[:minibatch_segments]
                mb_prio = torch.ones(minibatch_segments, 1, device=device)
            else:
                adv = advantages.abs().sum(axis=1)
                prio_weights = torch.nan_to_num(adv**a, 0, 0, 0)
                prio_probs = (prio_weights + 1e-6) / (prio_weights.sum() + 1e-6)
                idx = torch.multinomial(prio_probs, minibatch_segments, replacement=True)
                mb_prio = (segments * prio_probs[idx, None]) ** -1.0

            mb_obs = observations[idx].reshape(-1, *obs_space.shape)
            mb_actions = actions[idx]
            mb_logprobs = logprobs[idx]
            mb_values = values[idx]
            mb_returns = advantages[idx] + mb_values
            mb_advantages = advantages[idx]

            logits, newvalue = policy(mb_obs)
            _, newlogprob, entropy = sample_fn(logits, action=mb_actions)

            newlogprob = newlogprob.reshape(mb_logprobs.shape)
            logratio = newlogprob - mb_logprobs
            ratio = logratio.exp()

            adv = mb_prio * (mb_advantages - mb_advantages.mean()) / (mb_advantages.std() + 1e-8)

            pg_loss1 = -adv * ratio
            pg_loss2 = -adv * torch.clamp(ratio, 1 - clip_coef, 1 + clip_coef)
            pg_loss = torch.max(pg_loss1, pg_loss2).mean()

            newvalue = newvalue.view(mb_returns.shape)
            v_clipped = mb_values + torch.clamp(newvalue - mb_values, -vf_clip, vf_clip)
            v_loss = 0.5 * torch.max(
                (newvalue - mb_returns) ** 2,
                (v_clipped - mb_returns) ** 2,
            ).mean()

            entropy_loss = entropy.mean()
            loss = pg_loss + config.vf_coef * v_loss - config.ent_coef * entropy_loss

            loss.backward()
            torch.nn.utils.clip_grad_norm_(policy.parameters(), config.max_grad_norm)
            optimizer.step()
            optimizer.zero_grad()

        epoch += 1
        train_total_s += time.time() - train_start

    wall_seconds = time.time() - benchmark_start
    vecenv.close()

    return BenchmarkResult(
        label=label,
        total_steps=global_step,
        wall_seconds=wall_seconds,
        sps=global_step / wall_seconds if wall_seconds > 0 else 0,
        eval_seconds=eval_total_s,
        train_seconds=train_total_s,
        eval_sps=global_step / eval_total_s if eval_total_s > 0 else 0,
        train_sps=global_step / train_total_s if train_total_s > 0 else 0,
        epochs=epoch,
        device=device,
        num_envs=config.num_envs,
    )


def print_comparison(results: list[BenchmarkResult]) -> None:
    """Print a comparison table of benchmark results."""
    print("\n" + "=" * 80)
    print(f"{'Label':<25} {'SPS':>12} {'Eval SPS':>12} {'Train SPS':>12} "
          f"{'Steps':>10} {'Time(s)':>8} {'Epochs':>7}")
    print("-" * 80)
    for r in results:
        print(f"{r.label:<25} {r.sps:>12,.0f} {r.eval_sps:>12,.0f} {r.train_sps:>12,.0f} "
              f"{r.total_steps:>10,} {r.wall_seconds:>8.1f} {r.epochs:>7}")
    print("=" * 80)

    if len(results) >= 2:
        base = results[0]
        for r in results[1:]:
            if base.sps > 0:
                ratio = r.sps / base.sps
                print(f"  {r.label} vs {base.label}: {ratio:.3f}x ({'+' if ratio >= 1 else ''}{(ratio - 1) * 100:.1f}%)")
    print()
