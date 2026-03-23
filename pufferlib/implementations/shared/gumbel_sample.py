"""Gumbel-max sampling (T1.1) + fused entropy (T2.1).

Copied from runescape-rl's fight_caves_rl.puffer.gumbel_sample module,
with JVM-specific imports removed. This is the core sampling optimization
being tested in this experiment.

T1.1 — Gumbel-max sampling:
  Replaces torch.multinomial with argmax(logits + Gumbel noise).
  Produces identical categorical distribution without softmax + CDF scan.

T2.1 — Fused entropy:
  Replaces the two-pass entropy path (softmax then p*log_p) with a single-pass
  computation using log_softmax:
    log_probs = log_softmax(logits)
    probs = exp(log_probs)
    entropy = -(probs * log_probs).sum(-1)

  The original PufferLib entropy path computes softmax on already-normalized
  logits — a redundant second pass. The fused version skips this.
"""
from __future__ import annotations

import torch
import pufferlib.pytorch


def _fused_entropy(normalized_logits: torch.Tensor) -> torch.Tensor:
    """Compute categorical entropy from log-softmax logits in a single pass."""
    min_real = torch.finfo(normalized_logits.dtype).min
    log_probs = torch.clamp(normalized_logits, min=min_real)
    probs = log_probs.exp()
    p_log_p = probs * log_probs
    return -p_log_p.sum(-1)


def sample_logits_gumbel(logits, action=None):
    """Drop-in replacement for pufferlib.pytorch.sample_logits.

    Both paths (sampling and log-prob recomputation) use fused entropy.
    The sampling path additionally uses Gumbel-max instead of multinomial.
    """
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
        # Gumbel-max sampling (T1.1)
        uniform = torch.rand_like(logits_padded).clamp(1e-10, 1.0)
        gumbel_noise = -torch.log(-torch.log(uniform))
        action = (logits_padded + gumbel_noise).argmax(dim=-1).int()
    else:
        batch = logits_padded[0].shape[0]
        action = action.view(batch, -1).T

    assert len(logits_padded) == len(action)
    logprob = pufferlib.pytorch.log_prob(normalized_logits, action)

    # Fused entropy (T2.1)
    logits_entropy = _fused_entropy(normalized_logits).sum(0)

    if is_discrete:
        return action.squeeze(0), logprob.squeeze(0), logits_entropy.squeeze(0)

    return action.T, logprob.sum(0), logits_entropy
