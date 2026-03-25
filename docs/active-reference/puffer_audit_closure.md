# Puffer Audit Closure — Verification of Findings

> Targeted verification of the 4 specific findings from the Puffer integration audit.
> Verified against the **installed** pufferlib-core (v3.0.3) and current config/code state.

---

## Bonus Finding: Version Pin Mismatch

**What the audit assumed:** pufferlib-core==3.0.17 (per `pyproject.toml` line 29)
**What is actually installed:** pufferlib-core==3.0.3 (per `.venv/.../pufferlib/__init__.py` line 48)

The reference codebase in `pufferlib/` (renamed from `OSRS-env-review/`) is version 3.0. The installed version is 3.0.3. The `pyproject.toml` pin says 3.0.17. All three differ. The installed 3.0.3 is what actually runs.

**Is it real:** Yes — the pin and installed version disagree.
**Does it matter now:** Low risk. The installed 3.0.3 works correctly with our code (verified below). But the mismatch means a fresh `pip install` from the lock file could install a different version.
**Follow-up PR:** Optional — update pyproject.toml pin to `3.0.3` to match reality, or reinstall to match the pin.

---

## Finding 1: RNN State Initialization

**What the audit suggested:** `self.lstm_h` and `self.lstm_c` are accessed in `evaluate()` but never created. PuffeRL.__init__() calls `policy.initial_state()`, which our policy lacks. Potential `AttributeError` when `use_rnn=True`.

**What the installed pufferlib-core 3.0.3 actually does:**

```python
# pufferl.py lines 137-142
if config["use_rnn"]:
    n = vecenv.agents_per_batch
    h = policy.hidden_size
    self.lstm_h = {i * n: torch.zeros(n, h, device=device) for i in range(total_agents // n)}
    self.lstm_c = {i * n: torch.zeros(n, h, device=device) for i in range(total_agents // n)}
```

The installed version creates `self.lstm_h` and `self.lstm_c` **directly as dicts**, using `policy.hidden_size` (not `policy.initial_state()`). Our `MultiDiscreteLSTMPolicy` has `self.hidden_size` set in `__init__` (lstm.py line 22). This matches perfectly.

The **reference 3.0 codebase** in `pufferlib/` used the older pattern (`policy.initial_state()` → `self.state`). The installed 3.0.3 has already migrated to the dict-based pattern that our code expects.

**Is it real:** No. The audit finding was based on the 3.0 reference codebase, not the installed 3.0.3 version.
**Does it matter now:** No. The installed version creates exactly the attributes our code accesses.
**Follow-up PR:** None needed.

---

## Finding 2: KL / Clipfrac Logging Metrics Outside `torch.no_grad()`

**What the audit suggested:** Our `train()` computes `old_approx_kl`, `approx_kl`, and `clipfrac` without `torch.no_grad()`, while PuffeRL uses it. This wastes memory by creating unnecessary autograd graph nodes.

**Verification — our code (trainer.py lines 381-383):**
```python
_losses["old_approx_kl"] += (-logratio).mean().item() / _n
_losses["approx_kl"] += ((ratio - 1) - logratio).mean().item() / _n
_losses["clipfrac"] += ((ratio - 1.0).abs() > clip_coef).float().mean().item() / _n
```
No `torch.no_grad()` context.

**Installed pufferlib 3.0.3 (pufferl.py lines 421-425):**
```python
# TODO: Only do this if we are KL clipping? Saves 1-2% compute
with torch.no_grad():
    old_approx_kl = (-logratio).mean()
    approx_kl = ((ratio - 1) - logratio).mean()
    clipfrac = ((ratio - 1.0).abs() > config["clip_coef"]).float().mean()
```
Explicitly under `torch.no_grad()`, with a comment noting it saves 1-2% compute.

**Confirmation:** These values are used **only for logging** — stored in `_losses` dict via `.item()`, never used in loss computation or backward pass. The loss computation (trainer.py lines 362-371) uses only `pg_loss`, `v_loss`, and `entropy_loss`.

**Is it real:** Yes. The missing `no_grad()` is confirmed.
**Does it matter now:** Minor. PufferLib's own comment says it saves 1-2% compute. The `.item()` calls immediately extract scalars, so the graph nodes are short-lived. But with many minibatches, the waste accumulates slightly.
**Follow-up PR:** Yes — trivial fix. Wrap lines 381-383 in `with torch.no_grad():`. One-line change.

---

## Finding 3: Reward Clamp at [-1, 1]

**What the audit suggested:** The hardcoded `r = torch.clamp(r, -1, 1)` in evaluate() may silently clip shaped rewards.

**Verification — reward_shaped_v2 coefficients (the active config):**

| Feature | Coefficient | Plausible per-step range | Contribution range |
|---------|-------------|-------------------------|-------------------|
| wave_clear | +1.0 | 0 or 1 | 0 to +1.0 |
| cave_complete | +1.0 | 0 or 1 | 0 to +1.0 |
| npc_kill | +0.1 | 0 to ~5 | 0 to +0.5 |
| correct_jad_prayer | +0.25 | 0 or 1 | 0 to +0.25 |
| damage_dealt | +0.02 | 0 to ~50 | 0 to +1.0 |
| jad_damage_dealt | +0.03 | 0 to ~50 | 0 to +1.5 |
| player_death | -1.0 | 0 or 1 | 0 to -1.0 |
| damage_taken | -0.02 | 0 to ~99 | 0 to -1.98 |
| food_used | -0.05 | 0 or 1 | 0 to -0.05 |
| prayer_potion_used | -0.05 | 0 or 1 | 0 to -0.05 |
| invalid_action | -0.02 | 0 or 1 | 0 to -0.02 |
| tick_penalty_base | -0.0005 | 1 | -0.0005 |
| wrong_jad_prayer | -0.25 | 0 or 1 | 0 to -0.25 |

**Realistic scenarios that exceed bounds:**

Positive exceedance:
- Wave clear + simultaneous NPC kills + damage dealt: `1.0 + 0.3 + 0.2 = 1.5` → clamped to 1.0
- This happens every time the agent clears a wave while dealing damage in the same tick

Negative exceedance:
- Player death + large damage taken: `-1.0 + (-0.02 × 50) = -2.0` → clamped to -1.0
- Death events routinely coincide with the damage that caused death

**Is it real:** Yes. With the actual `reward_shaped_v2` coefficients, rewards can plausibly exceed [-1, 1] in both directions during normal gameplay.
**Does it matter now:** Moderate. The clamp silently destroys signal differentiation — a wave_clear + 3 NPC kills looks identical to a wave_clear alone. Whether this meaningfully hurts training depends on how frequently these events occur and how much the lost gradient signal matters for the value function. It is a confirmed information loss, not just theoretical.
**Follow-up PR:** Worth investigating. Options:
  - Add a diagnostic counter/histogram of pre-clamp rewards to quantify actual clipping frequency
  - Widen the clamp (e.g., [-5, 5]) or remove it and rely on reward coefficient scaling
  - Scale coefficients so the theoretical max stays within [-1, 1]

---

## Finding 4: LR Scheduler Equivalence

**What the audit suggested:** Our `self.scheduler.step()` might diverge from PuffeRL's intended cosine schedule.

**Verification:**

Installed pufferlib 3.0.3 creates the scheduler (pufferl.py lines 201-203):
```python
epochs = config["total_timesteps"] // config["batch_size"]
self.scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)
```

Our trainer inherits this via `super().__init__()` and calls `self.scheduler.step()` at trainer.py line 403, gated by `config["anneal_lr"]`.

**Critical additional finding:** `anneal_lr` is set to `false` in **every single config file**:
- `smoke_fast_v2.yaml`: `anneal_lr: false`
- `train_fast_v2.yaml`: `anneal_lr: false`
- `train_fast_v2_mlp64.yaml`: `anneal_lr: false`
- `train_fast_v2_mlp128.yaml`: `anneal_lr: false`
- `train_fast_v2_lstm64.yaml`: `anneal_lr: false`
- Default in `factory.py`: `"anneal_lr": False`

The scheduler is created but **never stepped**. The learning rate is constant throughout training.

**Is it real:** Not currently relevant. The scheduler exists and is correctly inherited, but `anneal_lr=false` everywhere means it never executes.
**Does it matter now:** No. If a future config enables `anneal_lr`, it will use the same `CosineAnnealingLR` that PuffeRL creates.
**Follow-up PR:** None needed.

---

## Final Verdict

### Truly actionable now

**None are urgent.** No finding blocks training correctness.

### Real but non-blocking

1. **KL/clipfrac no_grad** — Real, confirmed, ~1-2% compute waste per PufferLib's own estimate. Trivial fix.
2. **Reward clamp** — Real, confirmed that shaped rewards can exceed [-1, 1] with current coefficients. Information loss on wave-clear and death steps. Worth quantifying.
3. **Version pin mismatch** — Real. pyproject.toml says 3.0.17, installed is 3.0.3.

### Not actually issues

1. **RNN state initialization** — Not an issue. The installed pufferlib 3.0.3 creates `self.lstm_h`/`self.lstm_c` directly as dicts, matching our code exactly.
2. **LR scheduler divergence** — Not an issue. Scheduler is inherited correctly and `anneal_lr=false` in all configs means it never runs.

### Follow-up PRs worth doing (in priority order)

1. **Reward clamp diagnostic** (medium value) — Add pre-clamp reward histogram/counter to instrumentation so we can quantify actual clipping frequency. Then decide whether to widen clamp or rescale coefficients.
2. **KL/clipfrac no_grad** (low value, trivial) — Wrap 3 lines in `with torch.no_grad():`. Saves ~1-2% compute.
3. **Version pin alignment** (low value, hygiene) — Update `pyproject.toml` to pin `pufferlib-core==3.0.3` to match reality.
