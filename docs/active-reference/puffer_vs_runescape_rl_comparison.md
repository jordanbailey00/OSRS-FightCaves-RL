# PufferLib vs runescape-rl Integration Comparison

> Comparison of PufferLib 3.0 reference patterns against runescape-rl implementation.
> Purpose: identify alignment, divergences, and integration gaps.

---

## Integration Point Map

| Integration Point | Puffer File | Our File | Status |
|---|---|---|---|
| Trainer base class | `python_pufferl.py:PuffeRL` | `puffer/trainer.py:ConfigurablePuffeRL` | Subclass + override |
| evaluate() loop | `python_pufferl.py:214-329` | `puffer/trainer.py:132-270` | Full override |
| train() loop | `python_pufferl.py:332-467` | `puffer/trainer.py:272-437` | Full override |
| Action sampling | `pytorch.py:sample_logits` | `puffer/gumbel_sample.py:sample_logits_gumbel` | Replaced |
| Vectorized env | `vector.py:Serial/Multiprocessing` | `envs/vector_env.py`, `envs_fast/fast_vector_env.py` | Custom impls using Puffer protocol |
| Buffer allocation | `pufferlib.py:set_buffers` | Called in all vecenv constructors | Aligned |
| Space construction | `spaces.py:joint_space` | Called in all vecenv constructors | Aligned |
| send/recv protocol | `vector.py:send_precheck/recv_precheck` | Called in all vecenv send/recv | Aligned |
| Policy layer init | `pytorch.py:layer_init` | `policies/lstm.py`, `policies/mlp.py` | Aligned |
| Advantage computation | `python_pufferl.py:compute_puff_advantage` | `puffer/trainer.py` (calls it) | Aligned |
| Priority replay | `python_pufferl.py` train() | `puffer/trainer.py` train() | Extended (α=0 fast path) |
| Config management | `pufferl.py:load_config` (INI) | `puffer/factory.py` (YAML + deep merge) | Custom |
| Logging | `python_pufferl.py:mean_and_log + print_dashboard` | Custom WandbRunLogger + instrumentation | Extended |
| Policy interface | `models.py:forward_eval → (logits, value, state)` | `policies/lstm.py:forward_eval → (logits, value)` | Diverged |
| RNN state | `python_pufferl.py:self.state = {id: (h,c)}` | `puffer/trainer.py:self.lstm_h, self.lstm_c` | Diverged |
| vecenv.recv() | `vector.py: → 7 values` | Custom envs: → 8 values (added teacher_actions) | Extended |

---

## Detailed Comparison by Integration Point

### 1. Trainer: ConfigurablePuffeRL extends PuffeRL

**What Puffer expects:**
- Instantiate `PuffeRL(config, vecenv, policy, logger)` and call `evaluate()` / `train()` in a loop.
- These methods are designed as a matched pair sharing buffer layout assumptions.

**What we do:**
- `ConfigurablePuffeRL` subclasses `PuffeRL`, calls `super().__init__()`, then overrides both `evaluate()` and `train()` entirely.
- Adds control knobs: `dashboard_enabled`, `checkpointing_enabled`, `profiling_enabled`, `utilization_enabled`, `logging_enabled`, `instrumentation_enabled`.
- Adds granular perf_counter instrumentation across all subsections.

**Alignment assessment:** **Broadly aligned, with care needed.**
- The `__init__` inheritance is correct — buffer allocation, optimizer setup, and config parsing all come from the parent.
- Overriding both evaluate() and train() is risky but acceptable since they were overridden together (maintaining buffer layout consistency).
- The control knobs are a clean extension that doesn't break the base contract.

**File:** `runescape-rl/src/rl/fight_caves_rl/puffer/trainer.py:66-98`

---

### 2. evaluate() Override

**What Puffer expects:**
- `recv()` returns 7 values: `(obs, rew, done, trunc, info, env_id, mask)`
- `policy.forward_eval(obs, state)` returns 3 values: `(logits, value, state)`
- RNN state stored in `self.state` dict, keyed by `env_id.start`, values are `(h, c)` tuples
- Sampling via `pufferlib.pytorch.sample_logits(logits)` (torch.multinomial internally)
- Rewards clamped to `[-1, 1]`

**What we do:**
- `recv()` returns 8 values — adds `ta` (teacher actions) between truncations and info
- `policy.forward_eval(obs, state)` returns 2 values: `(logits, value)` — state is mutated in-place via dict
- RNN state stored in `self.lstm_h` and `self.lstm_c` as separate dicts
- Sampling via `sample_logits_gumbel(logits)` (Gumbel-max trick — mathematically equivalent but faster)
- Rewards clamped to `[-1, 1]` (same)
- State dict includes additional fields: `reward`, `done`, `env_id`, `mask` (used by policy for done-masking)

**Divergences:**

| Aspect | Puffer | Ours | Assessment |
|--------|--------|------|------------|
| recv() tuple size | 7 | 8 (+ teacher_actions) | **Intentional** — domain-specific data channel. Non-breaking since our envs and trainer are matched. |
| forward_eval returns | 3 (logits, value, state) | 2 (logits, value) | **Intentional redesign** — state mutation via dict instead of return value. Cleaner for done-masking. Breaks compatibility with Puffer's default policy interface. |
| RNN state structure | `self.state = {id: (h, c)}` | `self.lstm_h`, `self.lstm_c` separate dicts | **Potential gap** — these attributes are NOT created by PuffeRL.__init__. If `use_rnn=True`, the parent creates `self.state` via `policy.initial_state()`, but our policy lacks that method. See [RNN State Gap](#rnn-state-gap) below. |
| Sampling | torch.multinomial | Gumbel-max argmax | **Intentional optimization** (T1.1) — same distribution, lower overhead |
| Done-mask on LSTM | Not in base evaluate | Policy handles it internally | **Intentional** — domain-appropriate LSTM reset on death |

**Files:**
- `puffer/trainer.py:132-270` (our evaluate)
- `policies/lstm.py:65-85` (our forward_eval)
- `puffer/gumbel_sample.py` (our sampling)

---

### 3. train() Override

**What Puffer expects:**
- Recompute advantages each minibatch using latest `self.values` and `self.ratio`
- Priority sample via `multinomial(|adv|^α)` with IS correction
- Forward via `policy(obs)` → `(logits, value)`
- Recompute log-prob via `sample_logits(logits, action=mb_actions)`
- KL/clipfrac tracked under `torch.no_grad()` for efficiency
- In-epoch cosine LR annealing
- Returns `logs` dict

**What we do:**
- Same advantage recomputation pattern (calls `compute_puff_advantage`)
- **Extended:** When `α=0`, uses `torch.randperm` instead of `multinomial` (fast path for uniform sampling)
- Forward via `policy(obs, state)` with state dict — matches our forward() signature
- Recompute log-prob via `sample_logits_gumbel(logits, action=mb_actions)` (fused entropy, T2.1)
- KL/clipfrac computed **without** `torch.no_grad()` context
- LR annealing via `self.scheduler.step()` instead of in-epoch manual computation
- Returns `None` instead of `logs`
- Omits reading `mb_rewards`, `mb_terminals`, `mb_truncations`, `mb_ratio` from buffers (they're unused in loss)

**Divergences:**

| Aspect | Puffer | Ours | Assessment |
|--------|--------|------|------------|
| α=0 fast path | Not present | `randperm` instead of `multinomial` | **Good optimization** — avoids unnecessary priority computation when disabled |
| KL/clipfrac no_grad | Under `torch.no_grad()` | Without | **Minor gap** — creates unnecessary gradient graph nodes for metrics. Wastes a small amount of memory per minibatch. No correctness issue. |
| LR schedule | Manual cosine in-loop | `scheduler.step()` post-loop | **Intentional** — delegated to PyTorch scheduler. Must ensure schedule matches expected behavior. |
| Return value | Returns `logs` dict | Returns `None` | **Intentional** — trainer handles logging differently. Any code expecting `train()` to return logs would break. |
| Unused buffer reads | Reads mb_rewards/terminals/etc | Omits them | **Good cleanup** — PuffeRL reads these but never uses them in the loss computation |
| Entropy computation | `pufferlib.pytorch.sample_logits` | `sample_logits_gumbel` (fused, T2.1) | **Intentional optimization** — single-pass entropy via `exp(log_softmax)` instead of `softmax * log_softmax` |

**File:** `puffer/trainer.py:272-437`

---

### 4. Environment: Custom Vectorized Envs

**What Puffer expects:**
- Environment implements send/recv protocol via `pufferlib.vector.send_precheck`/`recv_precheck`
- Buffers allocated via `pufferlib.set_buffers(self)`
- Spaces defined via `single_observation_space`, `single_action_space`, joint via `pufferlib.spaces.joint_space`
- Class-level `reset = pufferlib.vector.reset` and `step = pufferlib.vector.step`
- Environments auto-reset on terminal/truncation inside `send()`

**What we do:**
- All three backends (HeadlessBatchVecEnv, FastKernelVecEnv, SubprocessHeadlessBatchVecEnv) follow this contract exactly:
  - `reset = pufferlib.vector.reset` (class attribute)
  - `step = pufferlib.vector.step` (class attribute)
  - `pufferlib.set_buffers(self)` called in `__init__`
  - `pufferlib.spaces.joint_space()` for space construction
  - `send_precheck`/`recv_precheck` called in send/recv
  - Auto-reset on done inside `send()`

**Alignment assessment:** **Strongly aligned.**

The Puffer vectorization contract is faithfully implemented. Key differences:
- We don't use Puffer's built-in `Serial`/`Multiprocessing`/`Ray` backends — we implement our own vectorization (JVM bridge, subprocess IPC, embedded JVM) while maintaining the exact same protocol.
- `recv()` returns 8 values instead of 7 (teacher_actions added)
- The `emulated` attribute is set for compatibility but uses a minimal/stub value

**Files:**
- `envs/vector_env.py:60-362` (HeadlessBatchVecEnv)
- `envs_fast/fast_vector_env.py:92-460` (FastKernelVecEnv)
- `envs/subprocess_vector_env.py:78+` (SubprocessHeadlessBatchVecEnv)

---

### 5. Policy: Custom LSTM with Dict-Based State

**What Puffer expects:**
- `forward(obs)` returns `(logits, value)` for training
- `forward_eval(obs, state)` returns `(logits, value, state)` for rollout
- `initial_state(batch_size, device)` returns `(h, c)` tuple
- State is opaque to the trainer — passed in, received back

**What we do:**
- `forward(obs, state)` returns `(logits, value)` — adds state parameter
- `forward_eval(obs, state)` returns `(logits, value)` — 2 values, not 3
- State is a mutable dict: `{"lstm_h": tensor, "lstm_c": tensor, "reward": tensor, "done": tensor, ...}`
- Policy mutates state dict in-place: `state["lstm_h"] = next_h`
- No `initial_state()` method — trainer is expected to create zero tensors directly
- Done-masking applied inside forward_eval: zeros out LSTM state for done envs

**Divergence assessment:** **Intentional redesign, but creates coupling.**

The dict-based state pattern is a reasonable design choice — it's more flexible (can carry additional context like done flags) and avoids the ambiguity of tuple indexing. However:
- It breaks compatibility with PuffeRL's default evaluate/train
- The trainer and policy MUST be deployed together — swapping either in isolation would fail
- This is fine as long as both sides are maintained in sync

**File:** `policies/lstm.py:14-150`

---

### 6. RNN State Gap {#rnn-state-gap}

**What Puffer expects:**
```python
# In PuffeRL.__init__():
if config['use_rnn']:
    self.state = {i*n: policy.initial_state(n, device=device) ...}

# In PuffeRL.evaluate():
state = self.state[env_id.start]
logits, value, state = policy.forward_eval(obs, state)
self.state[env_id.start] = state
```

**What we do:**
```python
# In ConfigurablePuffeRL.evaluate():
state["lstm_h"] = self.lstm_h[env_id.start]  # self.lstm_h not created by __init__
logits, value = policy.forward_eval(obs, state)
self.lstm_h[env_id.start] = state["lstm_h"]  # Mutated in-place by policy
```

**The gap:** `self.lstm_h` and `self.lstm_c` are NOT created by `PuffeRL.__init__()` or `ConfigurablePuffeRL.__init__()`. When `use_rnn=True`:
1. PuffeRL.__init__ tries to call `policy.initial_state()` — our policy doesn't have this method
2. Even if it did, it would create `self.state`, not `self.lstm_h`/`self.lstm_c`

**Possible explanations:**
- The installed `pufferlib-core==3.0.17` may handle RNN init differently than the 3.0 reference
- The `initial_state` call may be wrapped in a try/except in the installed version
- There may be a monkey-patch or version-specific behavior we haven't traced

**Risk:** If the installed version doesn't provide `self.lstm_h`/`self.lstm_c`, running with `use_rnn=True` would crash with `AttributeError`. Multiple configs set `use_rnn: true` (`smoke_fast_v2.yaml`, `train_fast_v2.yaml`), suggesting this path IS exercised — so there must be a mechanism making it work that differs from the 3.0 reference.

**Recommendation:** This deserves investigation. Verify the installed pufferlib-core 3.0.17's `PuffeRL.__init__` to confirm how RNN state is actually initialized.

---

### 7. Reward Handling

**What Puffer expects:**
- Rewards are returned from the environment and clamped to `[-1, 1]` in evaluate()
- No built-in reward shaping infrastructure
- Shaping should happen inside the environment

**What we do:**
- V1 Bridge: Python reward functions applied inside the vecenv's `_apply_step_response()`, converting raw observations to shaped rewards before returning from `recv()`
- V2 Fast: Feature-based reward weighting via `FastRewardAdapter.weight_batch()` — JVM emits reward feature vectors, adapter applies configurable coefficient weights
- Registry-driven reward configuration (reward_sparse_v0, reward_shaped_v0, reward_shaped_v2, etc.)
- Rewards arrive at the trainer already shaped — the `[-1, 1]` clamp in evaluate() then bounds them

**Alignment assessment:** **Aligned with Puffer's intent.**
Puffer expects reward shaping inside the env, and that's what we do. The reward adapter pattern is a clean domain-specific extension. The `[-1, 1]` clamp may be too aggressive for some shaped reward scales — worth monitoring.

**Files:**
- `envs/vector_env.py` (_apply_step_response)
- `envs_fast/fast_reward_adapter.py`
- `rewards/registry.py`

---

### 8. Vectorization: Custom Backends vs Puffer Backends

**What Puffer provides:**
- `Serial` (single-process), `Multiprocessing` (shared memory), `Ray` (distributed)
- Shared memory via `multiprocessing.RawArray`
- Semaphore-based synchronization
- Zero-copy batching for contiguous workers

**What we do:**
- **HeadlessBatchVecEnv**: Direct JVM bridge — single-process, all envs in one JVM runtime. No IPC needed. Implements Puffer protocol on top of direct bridge calls.
- **FastKernelVecEnv**: Embedded JVM via JPype — similar to HeadlessBatchVecEnv but with optimized fast kernel (C-like JVM performance). Pre-allocated scratch buffers (B1.5).
- **SubprocessHeadlessBatchVecEnv**: Multi-process with Pipe or shared-memory transport. Each subprocess runs its own HeadlessBatchVecEnv or FastKernelVecEnv. Master aggregates results.

**Divergence assessment:** **Intentional and appropriate.**
We can't use Puffer's built-in backends because our environment is a JVM process, not a Python-native env. The protocol compliance (send_precheck, recv_precheck, set_buffers, joint_space) ensures the trainer doesn't need to know about the backend difference.

---

### 9. Logging & Instrumentation

**What Puffer provides:**
- `mean_and_log()` aggregates stats into flat dict
- `print_dashboard()` renders Rich terminal dashboard
- Optional Neptune/WandB logger integration
- `Profile` class for section-level timing

**What we do:**
- Preserve `mean_and_log()` and `print_dashboard()` from base (with enable/disable flags)
- Add `_TrainerInstrumentation` class with microsecond-level timing buckets
- Add `WandbRunLogger` for experiment tracking
- Add `SmokeLogger` callback interface
- Instrumentation covers: recv, send, forward, backward, buffer writes, advantage, loss, optimizer, scheduler, checkpoint

**Alignment assessment:** **Well-aligned extension.**
We preserve the base logging and add domain-specific instrumentation on top. The enable/disable flags prevent overhead in production.

---

### 10. Configuration

**What Puffer expects:**
- INI-file based config loaded via `load_config(env_name)`
- Flat key-value structure
- CLI override via `--train.key value` pattern

**What we do:**
- YAML-based config with deep merge semantics
- Hierarchical structure: `train:`, `env:`, `policy:`, `reward_config:`, `curriculum_config:`
- `DEFAULT_SMOKE_TRAIN_CONFIG` dict as programmatic default
- `load_smoke_train_config(path)` → deepcopy default + YAML overlay

**Divergence assessment:** **Reasonable customization.**
YAML is more expressive than INI for nested config. The deep merge pattern is clean. We lose Puffer's CLI override capability but gain programmatic config composition.

**File:** `puffer/factory.py:40-93` (defaults), `puffer/factory.py:225-231` (loading)

---

## Where We Align

1. **Buffer allocation and layout** — `pufferlib.set_buffers()`, segment/horizon indexing, buffer dtype/shape
2. **send/recv protocol** — `send_precheck`/`recv_precheck`, flag-based state machine
3. **Advantage computation** — Direct call to `compute_puff_advantage()` with same parameters
4. **PPO loss structure** — Clipped policy loss, clipped value loss, entropy bonus, gradient norm clipping
5. **Space construction** — `pufferlib.spaces.joint_space()`, single_observation_space/single_action_space contract
6. **Layer initialization** — `pufferlib.pytorch.layer_init()` for policy heads
7. **Reward clamping** — `[-1, 1]` in evaluate()
8. **Profiling integration** — Preserves `self.profile` section timing
9. **Checkpoint save/load** — Inherits from base PuffeRL
10. **Dashboard rendering** — Preserved with enable/disable control

---

## Where We Diverge

| Area | Nature | Risk Level |
|------|--------|------------|
| RNN state mechanism | Dict-based vs tuple-based | **Medium** — coupling between policy and trainer |
| Policy interface | 2-return forward_eval, no initial_state | **Medium** — breaks default Puffer policy swap |
| Action sampling | Gumbel-max vs multinomial | **Low** — mathematically equivalent |
| Entropy computation | Fused single-pass | **Low** — numerically equivalent |
| recv() return tuple | 8 values vs 7 | **Low** — additive, non-breaking for trainer |
| Vectorization backend | JVM-based vs Python multiprocess | **Low** — protocol compliant |
| Config format | YAML vs INI | **Low** — different serialization, same concepts |
| Logging | Extended instrumentation | **Low** — additive |
| KL/clipfrac gradient tracking | Without no_grad vs with | **Low** — memory waste, no correctness issue |
| LR schedule | scheduler.step() vs manual cosine | **Low** — same intent, verify equivalence |
| train() return value | None vs logs dict | **Low** — our caller doesn't use it |

---

## Top 5 Most Important Integration Gaps/Opportunities

### 1. RNN State Initialization Gap
**Gap:** `self.lstm_h`/`self.lstm_c` are accessed in evaluate() but never created by any `__init__`. The parent PuffeRL creates `self.state` via `policy.initial_state()`, but our policy lacks that method.
**Risk:** AttributeError when `use_rnn=True` unless the installed pufferlib 3.0.17 handles this differently.
**Action needed:** Verify installed version's init behavior. If there IS a gap, either add `initial_state()` to the policy and convert `self.state` to `lstm_h`/`lstm_c` in `__init__`, or initialize them directly in `ConfigurablePuffeRL.__init__`.

### 2. KL/Clipfrac Gradient Tracking in train()
**Gap:** PuffeRL computes `old_approx_kl`, `approx_kl`, and `clipfrac` under `torch.no_grad()`. Our override computes them without. These values are used only for logging, never for loss/gradient computation.
**Risk:** Minor — unnecessary autograd graph construction. Wastes memory proportional to the computation graph for these metrics.
**Action needed:** Wrap in `torch.no_grad()`. Trivial fix.

### 3. Reward Clamping May Be Too Aggressive
**Gap:** `r = torch.clamp(r, -1, 1)` is inherited from PuffeRL. Our shaped rewards (e.g., NPC damage delta, player damage delta) have configurable coefficients that could produce rewards outside [-1, 1] before clamping.
**Risk:** If shaped rewards intentionally use larger magnitudes, the clamp silently destroys the signal differentiation between reward components.
**Action needed:** Audit whether shaped reward outputs ever exceed [-1, 1] in practice. If they do, either rescale coefficients or remove/widen the clamp.

### 4. Learning Rate Schedule Divergence
**Gap:** PuffeRL uses in-loop manual cosine annealing with `min_lr_ratio`. Our override uses `self.scheduler.step()` post-loop.
**Risk:** If the scheduler isn't configured to match PuffeRL's cosine formula (including `min_lr_ratio`), the learning rate trajectory will differ. This could affect training stability.
**Action needed:** Verify `self.scheduler` is a CosineAnnealingLR with matching parameters, or document the intentional schedule difference.

### 5. Dual Policy Interface Creates Coupling
**Gap:** Our policy uses dict-based state passing (2-value return from forward_eval), while PuffeRL expects tuple-based state passing (3-value return). This means:
- Our policy can only work with our custom trainer
- Our trainer can only work with our custom policy
- We can't use Puffer's default models or training loop without adaptation
**Risk:** This is fine for production but limits our ability to quickly test with Puffer's reference implementations for debugging.
**Action needed:** No immediate action, but be aware of this coupling when debugging training issues.

---

## Top 5 Things We Are Doing Correctly and Should Preserve

### 1. Faithful Puffer Vector Protocol Compliance
All three vecenv backends correctly implement the Puffer contract: `set_buffers()`, `joint_space()`, `send_precheck`/`recv_precheck`, class-level `reset`/`step`, and the flag state machine. This means the trainer doesn't need backend-specific logic. **Keep this.**

### 2. Advantage Computation Using Puffer's Implementation
We call `pufferlib.pufferl.compute_puff_advantage()` directly with no modifications. This ensures we get the exact same V-trace/GAE computation as Puffer, including any future optimizations (CUDA kernel). **Keep this.**

### 3. Buffer Layout Consistency Between evaluate() and train()
Despite overriding both methods, our implementations maintain the exact same buffer indexing: `batch_rows`, `ep_lengths`, `ep_indices`, `free_idx`, and the `(segments, horizon)` layout. This is the most critical invariant and we've preserved it perfectly. **Keep this.**

### 4. Gumbel-Max Sampling (T1.1) and Fused Entropy (T2.1)
These are mathematically equivalent to PuffeRL's defaults but faster. The Gumbel-max trick avoids the overhead of softmax + multinomial. Fused entropy avoids redundant softmax computation. Both produce identical distributions. **Keep this.**

### 5. Domain-Appropriate Reward Architecture
The reward adapter pattern (V2 fast: JVM emits feature vectors, Python applies configurable weights) is a clean separation of concerns. It allows reward experimentation without recompiling the JVM kernel. The V1 Python reward functions provide flexibility for complex shaping. Both approaches deliver shaped rewards to the trainer through the standard Puffer buffer pathway. **Keep this.**

---

## Summary Assessment

### Is Puffer now well understood?
**Yes.** The architecture, control flow, buffer layout, send/recv protocol, advantage computation, priority sampling, and intended extension points are thoroughly mapped. The reference codebase and PDF docs are consistent. The main uncertainty is minor version differences between our pinned 3.0.17 and the 3.0 reference.

### Is our integration broadly aligned or does it have meaningful gaps?
**Broadly aligned with a few specific gaps.** The fundamental contract (buffer allocation, vectorization protocol, advantage computation, PPO loss structure) is correctly implemented. The gaps are concentrated in the RNN state management interface and a few minor efficiency issues (no_grad for metrics, LR schedule verification).

### Top 3 Most Important Integration Gaps/Opportunities
1. **RNN state initialization** — `self.lstm_h`/`self.lstm_c` may not be created correctly by PuffeRL.__init__ when `use_rnn=True`. Needs verification against installed version.
2. **KL/clipfrac gradient tracking** — Missing `torch.no_grad()` wrapper for logging-only metrics in train(). Trivial to fix.
3. **Reward clamping at [-1, 1]** — May silently clip shaped rewards. Needs audit of actual reward magnitudes.

### Top 3 Things to Preserve
1. **Puffer vector protocol compliance** across all three env backends
2. **Direct use of `compute_puff_advantage()`** without modification
3. **Buffer layout consistency** between evaluate() and train()

### Findings That Deserve Follow-Up Implementation PRs
1. **RNN state init verification** — Confirm how pufferlib-core 3.0.17 initializes RNN state, and add explicit `lstm_h`/`lstm_c` creation in `ConfigurablePuffeRL.__init__` if needed
2. **Add `torch.no_grad()` to KL/clipfrac** in train() — one-line fix, saves memory
3. **Audit shaped reward magnitudes** — check if any reward config produces values outside [-1, 1] before clamping
4. **Verify LR scheduler equivalence** — confirm `self.scheduler` matches PuffeRL's cosine schedule with `min_lr_ratio`
5. **Consider adding `initial_state()` to policy** — even if unused by our trainer, having it would allow testing with PuffeRL's default evaluate() for debugging
