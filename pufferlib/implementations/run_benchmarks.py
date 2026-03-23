#!/usr/bin/env python3
"""Run all benchmarks: env-only + stock vs RS-style training.

Usage:
    cd /home/joe/projects/runescape-rl/claude/pufferlib
    python3 implementations/run_benchmarks.py [--device cuda|cpu] [--runs N] [--timesteps N]
"""
import argparse
import os
import sys
import time
import warnings
from collections import defaultdict
from dataclasses import dataclass, field

warnings.filterwarnings("ignore")

# Ensure pufferlib root is on path
PUFFERLIB_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if PUFFERLIB_ROOT not in sys.path:
    sys.path.insert(0, PUFFERLIB_ROOT)

import numpy as np
import torch
import torch.nn as nn
import pufferlib
import pufferlib.pytorch
import pufferlib.vector

# --- Gumbel-max sampling (from runescape-rl) ---

def _fused_entropy(normalized_logits):
    min_real = torch.finfo(normalized_logits.dtype).min
    log_probs = torch.clamp(normalized_logits, min=min_real)
    probs = log_probs.exp()
    return -(probs * log_probs).sum(-1)

def sample_logits_gumbel(logits, action=None):
    if isinstance(logits, torch.distributions.Normal):
        return pufferlib.pytorch.sample_logits(logits, action)
    is_discrete = isinstance(logits, torch.Tensor)
    if is_discrete:
        logits_padded = logits.unsqueeze(0)
    else:
        logits_padded = torch.nn.utils.rnn.pad_sequence(
            [l.transpose(0, 1) for l in logits], batch_first=False, padding_value=-torch.inf
        ).permute(1, 2, 0)
    normalized_logits = logits_padded - logits_padded.logsumexp(dim=-1, keepdim=True)
    if action is None:
        uniform = torch.rand_like(logits_padded).clamp(1e-10, 1.0)
        gumbel_noise = -torch.log(-torch.log(uniform))
        action = (logits_padded + gumbel_noise).argmax(dim=-1).int()
    else:
        batch = logits_padded[0].shape[0]
        action = action.view(batch, -1).T
    logprob = pufferlib.pytorch.log_prob(normalized_logits, action)
    logits_entropy = _fused_entropy(normalized_logits).sum(0)
    if is_discrete:
        return action.squeeze(0), logprob.squeeze(0), logits_entropy.squeeze(0)
    return action.T, logprob.sum(0), logits_entropy

# --- Policy ---

class RSPolicy(nn.Module):
    """RS-style 2-layer GELU MLP. Works for Discrete and MultiDiscrete."""
    def __init__(self, obs_size, action_sizes, hidden_size=128):
        super().__init__()
        self.encoder = nn.Sequential(
            pufferlib.pytorch.layer_init(nn.Linear(obs_size, hidden_size)),
            nn.GELU(),
            pufferlib.pytorch.layer_init(nn.Linear(hidden_size, hidden_size)),
            nn.GELU(),
        )
        self.action_heads = nn.ModuleList(
            pufferlib.pytorch.layer_init(nn.Linear(hidden_size, n), std=0.01)
            for n in action_sizes
        )
        self.value_head = pufferlib.pytorch.layer_init(nn.Linear(hidden_size, 1), std=1.0)
        self.hidden_size = hidden_size
        self._is_discrete = len(action_sizes) == 1

    def forward_eval(self, obs, state=None):
        h = self.encoder(obs.float().view(obs.shape[0], -1))
        if self._is_discrete:
            logits = self.action_heads[0](h)
        else:
            logits = tuple(head(h) for head in self.action_heads)
        return logits, self.value_head(h), state

    def forward(self, obs, state=None):
        h = self.encoder(obs.float().view(obs.shape[0], -1))
        if self._is_discrete:
            logits = self.action_heads[0](h)
        else:
            logits = tuple(head(h) for head in self.action_heads)
        return logits, self.value_head(h)

# --- GAE ---

def compute_gae(values, rewards, terminals, gamma, gae_lambda):
    segs, horizon = values.shape
    advantages = torch.zeros_like(values)
    last_gae = torch.zeros(segs, device=values.device)
    for t in reversed(range(horizon)):
        next_val = values[:, t] if t == horizon - 1 else values[:, t + 1]
        delta = rewards[:, t] + gamma * next_val * (1 - terminals[:, t]) - values[:, t]
        last_gae = delta + gamma * gae_lambda * (1 - terminals[:, t]) * last_gae
        advantages[:, t] = last_gae
    return advantages

# --- Benchmarks ---

def benchmark_env_only(env_cls, env_kwargs, num_envs, duration_s=10):
    env = env_cls(num_envs=num_envs, **env_kwargs)
    env.reset()
    atn_space = env.single_action_space
    if hasattr(atn_space, 'nvec'):
        actions = np.column_stack([
            np.random.randint(0, n, (1024, env.num_agents)) for n in atn_space.nvec
        ]).reshape(1024, env.num_agents, -1)
    elif hasattr(atn_space, 'n'):
        actions = np.random.randint(0, atn_space.n, (1024, env.num_agents))
    else:
        actions = np.random.uniform(-1, 1, (1024, env.num_agents)).astype(np.float32)
    tick = 0
    start = time.time()
    while time.time() - start < duration_s:
        env.step(actions[tick % 1024])
        tick += 1
    elapsed = time.time() - start
    sps = env.num_agents * tick / elapsed
    env.close()
    return sps

def benchmark_training(env_cls, env_kwargs, num_envs, action_sizes, obs_size,
                       sample_fn, device, total_timesteps, horizon=16, hidden=128):
    """Run a full PPO training loop and return SPS + timing breakdown."""
    env = env_cls(num_envs=num_envs, **env_kwargs)
    env.reset()
    total_agents = env.num_agents
    obs_space = env.single_observation_space
    atn_space = env.single_action_space

    batch_size = total_agents * horizon
    segments = batch_size // horizon
    minibatch_size = min(8192, batch_size)
    minibatch_segments = minibatch_size // horizon
    num_minibatches = max(1, batch_size // minibatch_size)

    policy = RSPolicy(obs_size, action_sizes, hidden).to(device)
    optimizer = torch.optim.Adam(policy.parameters(), lr=0.015, eps=1e-5)

    dtype_map = {np.float32: torch.float32, np.float64: torch.float64,
                 np.int8: torch.int8, np.uint8: torch.uint8, np.int32: torch.int32,
                 np.int64: torch.int64}
    obs_dtype = dtype_map.get(obs_space.dtype.type, torch.float32)
    atn_dtype = dtype_map.get(atn_space.dtype.type, torch.int32)

    observations = torch.zeros(segments, horizon, *obs_space.shape, dtype=obs_dtype, device=device)
    actions = torch.zeros(segments, horizon, *atn_space.shape, dtype=atn_dtype, device=device)
    values = torch.zeros(segments, horizon, device=device)
    logprobs = torch.zeros(segments, horizon, device=device)
    rewards = torch.zeros(segments, horizon, device=device)
    terminals = torch.zeros(segments, horizon, device=device)

    ep_lengths = torch.zeros(total_agents, device=device, dtype=torch.int32)
    ep_indices = torch.arange(total_agents, device=device, dtype=torch.int32)
    free_idx = total_agents

    global_step = 0
    epoch = 0
    eval_s = 0.0
    train_s = 0.0

    # Pre-generate actions for env.reset()
    if hasattr(atn_space, 'nvec'):
        reset_actions = np.column_stack([
            np.random.randint(0, n, env.num_agents) for n in atn_space.nvec
        ]).reshape(env.num_agents, -1)
    elif hasattr(atn_space, 'n'):
        reset_actions = np.random.randint(0, atn_space.n, env.num_agents)
    else:
        reset_actions = np.zeros(env.num_agents, dtype=np.float32)
    env.step(reset_actions)  # prime the env

    start = time.time()
    while global_step < total_timesteps:
        # EVALUATE
        t0 = time.time()
        full_rows = 0
        while full_rows < segments:
            obs_np = env.observations
            rew_np = env.rewards
            done_np = env.terminals

            o = torch.as_tensor(obs_np).to(device)
            r = torch.as_tensor(rew_np).to(device)
            d = torch.as_tensor(done_np).to(device)

            with torch.no_grad():
                logits, value, _ = policy.forward_eval(o, None)
                action, logprob, _ = sample_fn(logits)
                r = torch.clamp(r, -1, 1)

            l = ep_lengths[0].item()
            batch_rows = slice(0, total_agents)
            observations[batch_rows, l] = o
            actions[batch_rows, l] = action
            logprobs[batch_rows, l] = logprob
            rewards[batch_rows, l] = r
            terminals[batch_rows, l] = d.float()
            values[batch_rows, l] = value.flatten()

            global_step += total_agents
            ep_lengths[:] += 1
            if l + 1 >= horizon:
                full_rows += total_agents
                ep_lengths.zero_()

            act_np = action.cpu().numpy()
            env.step(act_np)

        eval_s += time.time() - t0

        # TRAIN
        t0 = time.time()
        advantages = compute_gae(values, rewards, terminals, 0.995, 0.90)
        for mb in range(num_minibatches):
            idx = torch.randperm(segments, device=device)[:minibatch_segments]
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
            adv = (mb_advantages - mb_advantages.mean()) / (mb_advantages.std() + 1e-8)
            pg_loss = torch.max(-adv * ratio, -adv * torch.clamp(ratio, 0.8, 1.2)).mean()
            newvalue = newvalue.view(mb_returns.shape)
            v_loss = 0.5 * ((newvalue - mb_returns) ** 2).mean()
            loss = pg_loss + 2.0 * v_loss - 0.001 * entropy.mean()
            loss.backward()
            torch.nn.utils.clip_grad_norm_(policy.parameters(), 1.5)
            optimizer.step()
            optimizer.zero_grad()

        epoch += 1
        train_s += time.time() - t0

    wall = time.time() - start
    env.close()
    return {
        "sps": global_step / wall,
        "eval_sps": global_step / eval_s if eval_s > 0 else 0,
        "train_sps": global_step / train_s if train_s > 0 else 0,
        "steps": global_step,
        "wall": wall,
        "eval_s": eval_s,
        "train_s": train_s,
        "epochs": epoch,
    }

# --- Environment configs ---

ENVS = {}

def register_env(name, cls_path, env_kwargs, num_envs, obs_size, action_sizes):
    ENVS[name] = {
        "cls_path": cls_path,
        "env_kwargs": env_kwargs,
        "num_envs": num_envs,
        "obs_size": obs_size,
        "action_sizes": action_sizes,
    }

register_env("OneStateWorld",
    "pufferlib.ocean.onestateworld.onestateworld.World",
    {}, 4096, 1, [2])

register_env("Asteroids",
    "pufferlib.ocean.asteroids.asteroids.Asteroids",
    {}, 4096, 104, [4])

register_env("Boids",
    "pufferlib.ocean.boids.boids.Boids",
    {"num_boids": 64, "margin_turn_factor": 0.0, "centering_factor": 0.0,
     "avoid_factor": 1.0, "matching_factor": 1.0},
    64, 256, [5, 5])

def load_env_cls(cls_path):
    module_path, cls_name = cls_path.rsplit(".", 1)
    import importlib
    mod = importlib.import_module(module_path)
    return getattr(mod, cls_name)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--device", default="cuda", choices=["cuda", "cpu"])
    parser.add_argument("--runs", type=int, default=3, help="Repeated runs for median")
    parser.add_argument("--timesteps", type=int, default=200_000)
    parser.add_argument("--env-only-duration", type=float, default=10.0)
    parser.add_argument("--envs", nargs="+", default=list(ENVS.keys()))
    args = parser.parse_args()

    device = args.device
    if device == "cuda" and not torch.cuda.is_available():
        print("CUDA not available, falling back to CPU")
        device = "cpu"

    print(f"Device: {device}")
    if device == "cuda":
        print(f"GPU: {torch.cuda.get_device_name(0)}")
    print(f"Runs per benchmark: {args.runs}")
    print(f"Timesteps per run: {args.timesteps:,}")
    print()

    all_results = {}

    for env_name in args.envs:
        if env_name not in ENVS:
            print(f"Unknown env: {env_name}, skipping")
            continue

        cfg = ENVS[env_name]
        env_cls = load_env_cls(cfg["cls_path"])
        print(f"{'='*70}")
        print(f"  {env_name}")
        print(f"  obs={cfg['obs_size']}, action_sizes={cfg['action_sizes']}, "
              f"num_envs={cfg['num_envs']}")
        print(f"{'='*70}")

        # Env-only
        print(f"  Env-only SPS ({args.env_only_duration}s)...")
        env_sps_runs = []
        for r in range(args.runs):
            sps = benchmark_env_only(env_cls, cfg["env_kwargs"], cfg["num_envs"],
                                     duration_s=args.env_only_duration)
            env_sps_runs.append(sps)
            print(f"    run {r+1}: {sps:,.0f}")
        env_sps = float(np.median(env_sps_runs))
        print(f"  Env-only median: {env_sps:,.0f}")

        # Stock training
        print(f"\n  Stock training (multinomial)...")
        stock_runs = []
        for r in range(args.runs):
            res = benchmark_training(
                env_cls, cfg["env_kwargs"], cfg["num_envs"],
                cfg["action_sizes"], cfg["obs_size"],
                pufferlib.pytorch.sample_logits, device, args.timesteps)
            stock_runs.append(res)
            print(f"    run {r+1}: {res['sps']:,.0f} SPS "
                  f"(eval={res['eval_s']:.1f}s train={res['train_s']:.1f}s)")
        stock_sps = float(np.median([r["sps"] for r in stock_runs]))

        # RS-style training
        print(f"\n  RS-style training (Gumbel+fused)...")
        rs_runs = []
        for r in range(args.runs):
            res = benchmark_training(
                env_cls, cfg["env_kwargs"], cfg["num_envs"],
                cfg["action_sizes"], cfg["obs_size"],
                sample_logits_gumbel, device, args.timesteps)
            rs_runs.append(res)
            print(f"    run {r+1}: {res['sps']:,.0f} SPS "
                  f"(eval={res['eval_s']:.1f}s train={res['train_s']:.1f}s)")
        rs_sps = float(np.median([r["sps"] for r in rs_runs]))

        ratio = rs_sps / stock_sps if stock_sps > 0 else 0
        print(f"\n  RESULTS for {env_name}:")
        print(f"    Env-only SPS:  {env_sps:>12,.0f}")
        print(f"    Stock SPS:     {stock_sps:>12,.0f}")
        print(f"    RS-style SPS:  {rs_sps:>12,.0f}")
        print(f"    RS/Stock:      {ratio:>12.3f}x")

        all_results[env_name] = {
            "env_sps": env_sps,
            "stock_sps": stock_sps,
            "rs_sps": rs_sps,
            "ratio": ratio,
            "stock_runs": stock_runs,
            "rs_runs": rs_runs,
            "env_sps_runs": env_sps_runs,
            "device": device,
        }
        print()

    # Summary
    print(f"\n{'='*70}")
    print(f"  SUMMARY")
    print(f"{'='*70}")
    print(f"{'Env':<16} {'Env-Only':>12} {'Stock':>12} {'RS-Style':>12} {'RS/Stock':>10}")
    print(f"{'-'*62}")
    for name, r in all_results.items():
        print(f"{name:<16} {r['env_sps']:>12,.0f} {r['stock_sps']:>12,.0f} "
              f"{r['rs_sps']:>12,.0f} {r['ratio']:>10.3f}x")
    print()

    # Detailed per-run table
    print(f"\nPer-run detail:")
    for name, r in all_results.items():
        print(f"\n  {name} (device={r['device']}):")
        print(f"    {'Run':>4} {'Stock SPS':>12} {'RS SPS':>12} {'S-Eval':>8} {'S-Train':>8} {'R-Eval':>8} {'R-Train':>8}")
        for i in range(len(r["stock_runs"])):
            sr = r["stock_runs"][i]
            rr = r["rs_runs"][i]
            print(f"    {i+1:>4} {sr['sps']:>12,.0f} {rr['sps']:>12,.0f} "
                  f"{sr['eval_s']:>8.2f} {sr['train_s']:>8.2f} "
                  f"{rr['eval_s']:>8.2f} {rr['train_s']:>8.2f}")


if __name__ == "__main__":
    main()
