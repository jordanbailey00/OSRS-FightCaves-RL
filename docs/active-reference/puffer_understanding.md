# PufferLib Deep Understanding Reference

> Source: PufferLib 3.0 reference codebase at `pufferlib/` (renamed from `OSRS-env-review/`) + PufferLib_Docs.pdf
> Our project pins: `pufferlib-core==3.0.17`

---

## 1. High-Level Architecture Map

```
┌──────────────────────────────────────────────────────────────────────┐
│                      Training Entry Point                           │
│              pufferlib.pufferl  (CLI + DDP wrapper)                 │
│                                                                      │
│   puffer train <env>  →  load_config()  →  _train()                │
│                             │                 │                      │
│                      load_env()        load_policy()                │
│                             │                 │                      │
│                     ┌───────▼─────────────────▼───────┐             │
│                     │     PuffeRL (python_pufferl.py)  │             │
│                     │     ┌────────────────────────┐   │             │
│                     │     │  evaluate()            │   │             │
│                     │     │    recv → forward →    │   │             │
│                     │     │    sample → store →    │   │             │
│                     │     │    send               │   │             │
│                     │     ├────────────────────────┤   │             │
│                     │     │  train()              │   │             │
│                     │     │    advantage →         │   │             │
│                     │     │    priority sample →   │   │             │
│                     │     │    forward → loss →    │   │             │
│                     │     │    backward → step     │   │             │
│                     │     └────────────────────────┘   │             │
│                     └───────────┬──────────────────────┘             │
│                                 │                                    │
│         ┌───────────────────────▼──────────────────────┐            │
│         │        Vectorized Environment Layer           │            │
│         │        pufferlib.vector                       │            │
│         │   ┌──────────┬──────────────┬────────────┐   │            │
│         │   │  Serial  │ Multiprocess │    Ray     │   │            │
│         │   └──────────┴──────────────┴────────────┘   │            │
│         │     send/recv + shared memory + semaphores    │            │
│         └───────────────────────┬──────────────────────┘            │
│                                 │                                    │
│         ┌───────────────────────▼──────────────────────┐            │
│         │           Environment Instances               │            │
│         │  ┌─────────────────┬───────────────────────┐ │            │
│         │  │  Native PufferEnv  │  Emulated (Gym/PZ) │ │            │
│         │  └─────────────────┴───────────────────────┘ │            │
│         └──────────────────────────────────────────────┘            │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 2. Control-Flow Map

### A. Full Training Loop

```
main()
  ├─ load_config(env_name)          # Parse INI + CLI overrides
  ├─ load_env(env_name, args)       # Create vectorized env
  │   └─ vector.make(creator, backend, num_envs, num_workers, ...)
  ├─ load_policy(args, vecenv)      # Create neural network
  │   └─ DefaultPolicy or custom
  └─ PuffeRL(config, vecenv, policy)
      └─ while global_step < total_timesteps:
          ├─ evaluate()              # Collect rollout data
          │   ├─ Reset RNN state (zero)
          │   └─ while full_rows < segments:
          │       ├─ vecenv.recv()            → obs, rew, done, trunc, info, env_id, mask
          │       ├─ torch.as_tensor(obs).to(device)
          │       ├─ policy.forward_eval(obs, state)  → logits, value, state
          │       ├─ sample_logits(logits)    → action, logprob, entropy
          │       ├─ clamp rewards to [-1, 1]
          │       ├─ Store: obs, action, logprob, reward, terminal, value  → buffers[batch_rows, l]
          │       ├─ Update ep_lengths, ep_indices, full_rows
          │       ├─ Collect info stats
          │       └─ vecenv.send(action.cpu().numpy())
          │
          ├─ train()                 # Update policy
          │   ├─ For each minibatch:
          │   │   ├─ compute_puff_advantage(values, rewards, terminals, ratio, ...)
          │   │   ├─ Priority sample: multinomial(|advantages|^α)
          │   │   ├─ Gather minibatch from buffers
          │   │   ├─ policy(mb_obs)  → logits, newvalue
          │   │   ├─ sample_logits(logits, action=mb_actions)  → _, logprob, entropy
          │   │   ├─ PPO clipped policy loss + clipped value loss - entropy bonus
          │   │   ├─ loss.backward()
          │   │   └─ clip_grad_norm → optimizer.step()
          │   └─ Log losses, explained variance, checkpoint
          │
          ├─ mean_and_log()          # Aggregate stats
          └─ print_dashboard()       # Rich terminal output
```

### B. Environment Step Cycle (Multiprocessing Backend)

```
                Main Process                    Worker Process
                ────────────                    ──────────────
    vecenv.async_reset(seed)
      │  set semaphores[:] = RESET
      │  send seed via pipe
      │                              ──→  recv seed
      │                                   envs.reset(seed)
      │                                   fill shared obs buffer
      │                                   semaphores[w] = MAIN  (ready)
      │
    vecenv.recv()
      │  poll semaphores for MAIN
      │  read obs/rew/done from shared memory
      │  recv infos via pipe
      │  set semaphores[batch] = SEND
      │  return (obs, rew, done, trunc, info, env_id, mask)
      │
    vecenv.send(actions)
      │  copy actions to shared memory
      │  set semaphores[batch] = STEP
      │                              ──→  read semaphores == STEP
      │                                   read actions from shared memory
      │                                   envs.step(actions)
      │                                   auto-reset done envs
      │                                   update shared obs/rew/done
      │                                   send infos via pipe
      │                                   semaphores[w] = INFO → MAIN
```

---

## 3. Major Subsystems and Responsibilities

### A. `pufferlib.py` — Core Primitives

| Component | Purpose |
|-----------|---------|
| `PufferEnv` | Abstract base for native vectorized envs. Requires: `single_observation_space`, `single_action_space`, `num_agents`. Methods: `reset()`, `step()`, `async_reset()`, `send()`, `recv()` |
| `set_buffers(env)` | Pre-allocates numpy arrays: `observations`, `rewards`, `terminals`, `truncations`, `masks`, `actions` on the env instance. Called once during env construction. |
| `EpisodeStats` | Wrapper tracking cumulative episode return and length |
| `unroll_nested_dict()` | Flattens nested info dicts for logging |

### B. `vector.py` — Vectorization Engine

| Component | Purpose |
|-----------|---------|
| `Serial` | Single-process sync vectorization. Good for debugging. |
| `Multiprocessing` | Multi-worker with shared memory + semaphore-based synchronization. Zero-copy optimization for contiguous worker batches. |
| `Ray` | Distributed vectorization via Ray actors. |
| `make()` | Factory function: `make(creator, backend, num_envs, num_workers, batch_size, ...)` |
| State machine | `RESET=0, STEP=1, SEND=2, RECV=3, CLOSE=4, MAIN=5, INFO=6` semaphore values |
| `send_precheck()` / `recv_precheck()` | Contract validation — ensures correct send/recv ordering |

**Key design decisions:**
- Shared memory via numpy arrays backed by `multiprocessing.RawArray` — no serialization overhead
- Busy-wait on semaphore flags instead of pipes/queues — lowest latency
- Contiguous worker blocks enable true zero-copy batching
- Multiple envs per worker — critical for fast envs where Python overhead dominates

### C. `emulation.py` — Environment Compatibility

| Component | Purpose |
|-----------|---------|
| `GymnasiumPufferEnv` | Wraps standard Gymnasium envs to PufferEnv API |
| `PettingZooPufferEnv` | Wraps PettingZoo multi-agent envs |
| `GymnaxPufferEnv` | Wraps JAX-based Gymnax envs |
| Space emulation | Flattens complex (Dict, Tuple) observation/action spaces to flat arrays using NumPy structured dtypes |

### D. `python_pufferl.py` — Training Loop (PuffeRL Class)

| Component | Purpose |
|-----------|---------|
| `PuffeRL.__init__()` | Allocates experience buffers, initializes optimizer, configures AMP, sets up RNN state |
| `evaluate()` | Rollout collection: recv → forward → sample → store → send |
| `train()` | Policy update: advantage → priority sample → forward → loss → backward → step |
| `compute_puff_advantage()` | GAE with V-trace corrections for off-policy importance weighting |
| `mean_and_log()` | Aggregates stats and sends to logger |
| `print_dashboard()` | Rich terminal dashboard with GPU stats |
| `save_checkpoint()` | Model + optimizer state persistence |

### E. `pytorch.py` — PyTorch Utilities

| Component | Purpose |
|-----------|---------|
| `layer_init(layer, std)` | Orthogonal weight init with configurable std |
| `sample_logits(logits, action)` | Categorical/Normal sampling with log-prob and entropy |
| `log_prob(logits, action)` | Log-probability computation for multi-discrete actions |
| `entropy(logits)` | Entropy computation for exploration bonus |
| `nativize_dtype/tensor()` | Convert emulated flat tensors back to structured types |

### F. `models.py` — Neural Network Components

| Component | Purpose |
|-----------|---------|
| `DefaultEncoder` | Linear encoder for Box observations |
| `DefaultDecoder` | Policy heads for discrete/multi-discrete/continuous actions + value head |
| `MinGRULayer` | Efficient GRU variant (Mamba-inspired) |
| `initial_state()` | Returns `(h, c)` tuple for RNN state initialization |
| `forward_eval()` | Fast inference path using LSTMCell (3x faster than LSTM) |
| `forward()` | Training path using full LSTM for sequence processing |

---

## 4. Intended Extension/Customization Points

### Users ARE expected to customize:

1. **Environment implementation** — Create custom `PufferEnv` subclass or wrap via `GymnasiumPufferEnv`
2. **Policy architecture** — Write standard PyTorch `nn.Module`. Break into `encode_observations()` + `decode_actions()` for LSTM integration
3. **Reward shaping** — Applied within environment, not in the training loop
4. **Configuration** — INI/YAML files for hyperparameters, env args, vec args
5. **Logging backend** — Neptune, WandB, or custom logger
6. **Observation/action spaces** — Define via Gymnasium spaces API

### Users are expected to stay CLOSE TO defaults for:

1. **Training loop** — `evaluate()` and `train()` are carefully optimized and interdependent. Overriding requires understanding buffer layout, indexing, advantage computation, and priority sampling.
2. **Vectorization internals** — The `send/recv` protocol, semaphore state machine, and shared memory layout should not be modified.
3. **Buffer allocation** — `set_buffers()` and the segment/horizon buffer layout are tightly coupled to the training loop.
4. **Advantage computation** — `compute_puff_advantage()` uses custom V-trace implementation that assumes specific buffer shapes.
5. **Priority sampling** — The `prio_alpha`/`prio_beta0` mechanism is built into `train()` and modifying it requires understanding importance weighting.
6. **Device placement** — The CPU-offload pattern, pinned memory, and GPU transfer flow are carefully designed.

---

## 5. Intended Default Paths

### Default Training Flow
```python
vecenv = pufferlib.vector.make(env_creator, backend=Multiprocessing, ...)
policy = DefaultPolicy(vecenv)  # or custom nn.Module
trainer = PuffeRL(config, vecenv, policy, logger)
while trainer.global_step < total_timesteps:
    trainer.evaluate()
    trainer.train()
trainer.close()
```

### Default Policy Pattern
```python
class MyPolicy(nn.Module):
    def __init__(self, vecenv):
        self.encoder = ...
        self.decoder = ...
        self.value_head = ...

    def forward(self, obs):
        hidden = self.encoder(obs)
        logits = self.decoder(hidden)
        value = self.value_head(hidden)
        return logits, value

    # For RNN support:
    def forward_eval(self, obs, state):
        hidden = self.encoder(obs)
        hidden, state = self.rnn_cell(hidden, state)
        logits = self.decoder(hidden)
        value = self.value_head(hidden)
        return logits, value, state  # NOTE: Returns 3 values

    def initial_state(self, batch_size, device):
        h = torch.zeros(...)
        c = torch.zeros(...)
        return h, c  # NOTE: Returns (h, c) tuple
```

### Default Environment Pattern
```python
class MyEnv(pufferlib.PufferEnv):
    def __init__(self, ...):
        self.single_observation_space = spaces.Box(...)
        self.single_action_space = spaces.Discrete(...)
        self.num_agents = N
        pufferlib.set_buffers(self)

    def reset(self, seed=None):
        self.observations[:] = initial_obs
        return self.observations, [{} for _ in range(self.num_agents)]

    def step(self, actions):
        # Update observations, rewards, terminals IN-PLACE
        self.observations[:] = next_obs
        self.rewards[:] = rewards
        self.terminals[:] = dones
        return self.observations, self.rewards, self.terminals, self.truncations, infos
```

---

## 6. Important Design Patterns

### Pattern 1: In-Place Buffer Updates
PufferLib minimizes memory allocation by pre-allocating all buffers and updating them in-place. Environments write directly into `self.observations`, `self.rewards`, etc. The vectorization layer reads from these same buffers via shared memory. **Never create new arrays during step().**

### Pattern 2: Semaphore State Machine
Worker synchronization uses integer flags in shared memory:
```
RESET(0) → MAIN(5) → SEND(2) → STEP(1) → INFO(6) → MAIN(5) → ...
```
The main process busy-waits on these flags. No locks, no queues, no pipes for synchronization. Pipes are reserved for info dicts only.

### Pattern 3: Segment-Based Experience Storage
Experience is stored as `(segments, horizon)` 2D arrays where:
- `segments` = number of independent trajectory chunks
- `horizon` = `bptt_horizon` (time steps per chunk)
- `batch_rows` maps env groups to segment indices
- `ep_lengths` tracks current position within each segment
- When a segment fills (`ep_length >= horizon`), a new segment index is allocated from `free_idx`

### Pattern 4: Priority Experience Replay
```python
# α controls priority strength: 0 = uniform, 1 = full priority
# β0 controls initial importance-sampling correction
advantages_sum = |advantages|.sum(axis=1)  # Sum over horizon
weights = advantages_sum^α                 # Priority weights
probs = weights / weights.sum()            # Normalized
idx = multinomial(probs, minibatch_segments)
IS_correction = (segments * probs[idx])^(-β_annealed)
```

### Pattern 5: V-Trace Off-Policy Correction
`compute_puff_advantage()` applies V-trace importance weighting when `self.ratio != 1` (i.e., when reusing experience from previous epochs). This clips the importance ratio to prevent large updates from stale data.

### Pattern 6: Dual LSTM Paths
- **Eval (rollout)**: Uses `LSTMCell` for single-step inference. 3x faster.
- **Train**: Uses full `LSTM` for sequence processing over the `bptt_horizon` dimension.
- State is stored per env group and zeroed at the start of each evaluate() call.

---

## 7. Critical Implementation Details

### Buffer Layout
```python
# Allocated in PuffeRL.__init__()
observations: (segments, horizon, *obs_shape)  # Pin-memory if cpu_offload
actions:      (segments, horizon, *atn_shape)
logprobs:     (segments, horizon)
rewards:      (segments, horizon)
terminals:    (segments, horizon)
values:       (segments, horizon)
ratio:        (segments, horizon)              # For V-trace

# Tracking arrays
ep_lengths:   (total_agents,)     int32        # Current position in horizon
ep_indices:   (total_agents,)     int32        # Current segment index
free_idx:     scalar int                       # Next available segment
```

### Reward Clamping
PufferLib clamps rewards to `[-1, 1]` by default in `evaluate()`:
```python
r = torch.clamp(r, -1, 1)
```
This is applied AFTER the environment returns rewards but BEFORE storing in buffers.

### Advantage Normalization
Advantages are normalized per-minibatch:
```python
adv = prio_weights * (advantages - advantages.mean()) / (advantages.std() + 1e-8)
```

### PPO Clipping
Both policy ratio and value function are clipped:
```python
pg_loss = max(-adv * ratio, -adv * clamp(ratio, 1-ε, 1+ε))
v_loss = max((v - returns)², (v_clipped - returns)²)
```

### Gradient Accumulation
When `minibatch_size > max_minibatch_size`:
```python
accumulate_minibatches = minibatch_size // max_minibatch_size
# Gradients accumulate, optimizer steps every accumulate_minibatches
```

### Learning Rate Schedule
In-epoch cosine annealing with configurable minimum ratio:
```python
lr = lr_min + 0.5 * (lr - lr_min) * (1 + cos(π * epoch/total_epochs))
```

---

## 8. What We Should Understand Before Modifying Our Integration

### Critical invariants:
1. **Buffer indexing**: `batch_rows` and `l` (position in horizon) must be consistent between `evaluate()` and `train()`. Any modification to one requires understanding the other.
2. **send_precheck / recv_precheck**: These enforce the send→recv→send alternation. Violating this causes assertion errors.
3. **RNN state management**: PuffeRL zeros all RNN state at the start of each `evaluate()` call. State is stored per env group (keyed by `env_id.start`). This means RNN state does NOT persist across evaluate() calls — each rollout starts fresh.
4. **Priority sampling couples advantage and loss**: The importance-sampling correction `mb_prio` must multiply the advantage in the loss. Removing priority without removing the IS correction (or vice versa) breaks the gradient estimator.
5. **Reward clamping at [-1, 1]**: This is hardcoded in `evaluate()`. If the environment provides shaped rewards outside this range, they will be silently clamped.
6. **`compute_puff_advantage` is a C extension when available**: Falls back to Python. The C version uses a custom CUDA kernel for the advantage computation. Performance-sensitive.
7. **`total_minibatches` vs `num_minibatches`**: PuffeRL uses `num_minibatches = update_epochs * (batch_size // minibatch_size)`. Each minibatch recomputes advantages from scratch using the latest `self.values` and `self.ratio`.
8. **`self.ratio` persists across minibatches**: Updated each minibatch with `self.ratio[idx] = ratio.detach()`. This enables V-trace correction within the same training step.

### Version sensitivity:
- Our project pins `pufferlib-core==3.0.17`
- The reference codebase is version `3.0`
- Minor differences in API surface are possible (e.g., `@record` decorator, `initial_state` handling)
- Always verify against the installed version when debugging integration issues
