# Puffer Integration Follow-Up Queue

> Source: Puffer audit closure (2026-03-22). Audit is complete. These are confirmed non-blocking items only.
> None of these block the current mainline (device=cuda promotion → E1.1).

---

## 1. Version Pin Alignment

**Priority:** Low (hygiene)
**Risk:** None to training. Reproducibility / environment-drift concern only.

**The mismatch:**
- `src/rl/pyproject.toml` line 29 pins `pufferlib-core==3.0.17`
- `.venv/.../pufferlib/__init__.py` line 48 reports `__version__ = "3.0.3"`
- A fresh `pip install` from the lock file could install a version that differs from what is actually tested

**Fix:** Update `pyproject.toml` to `pufferlib-core==3.0.3` to match the installed and verified version.

---

## 2. KL/clipfrac `torch.no_grad()`

**Priority:** Low (trivial cleanup)
**Risk:** None. Logging-only metrics. Estimated ~1-2% compute savings per PufferLib's own comment.

**The issue:**
- `trainer.py` lines 381-383 compute `old_approx_kl`, `approx_kl`, `clipfrac` without `torch.no_grad()`
- These values are extracted via `.item()` for logging only — never used in loss or backward
- Installed pufferlib 3.0.3 wraps the same computation in `with torch.no_grad():`

**Fix:** Wrap those 3 lines in `with torch.no_grad():`.

---

## 3. Reward Clamp Diagnostics

**Priority:** Medium (information-gathering before any behavior change)
**Risk:** No risk from the diagnostic itself. The underlying clamp is a confirmed information loss, but changing it without data would be premature.

**The issue:**
- `trainer.py` line 191: `r = torch.clamp(r, -1, 1)`
- With `reward_shaped_v2` coefficients, wave-clear + simultaneous NPC kills can produce rewards up to ~1.5
- Death + damage-taken can produce rewards down to ~-2.0
- Clipping is silent — no logging of frequency or magnitude

**Next step:** Small instrumentation PR to measure pre-clamp reward distribution. Track:
- frequency of clamp events per epoch
- magnitude of clipped rewards (max/min before clamp)
- which reward scenarios trigger clipping most often

Do NOT change clamp behavior until diagnostics show whether it matters in practice.

---

## Queue Status

| # | Item | Type | Blocks mainline? | Suggested timing |
|---|------|------|-------------------|-----------------|
| 1 | Version pin | Hygiene | No | Next small side PR |
| 2 | KL no_grad | Cleanup | No | Bundle with #1 |
| 3 | Reward clamp diagnostics | Instrumentation | No | After E1.1 stabilizes |
