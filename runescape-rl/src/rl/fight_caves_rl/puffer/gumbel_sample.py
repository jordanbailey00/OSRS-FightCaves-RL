"""Optimized sample_logits: Gumbel-max sampling (T1.1) + fused entropy (T2.1).

T1.1 — Gumbel-max sampling:
  Replaces torch.multinomial with argmax(logits + Gumbel noise).
  Produces identical categorical distribution without softmax + CDF scan.

T2.1 — Fused entropy:
  Replaces the two-pass entropy path (softmax then p*log_p) with a single-pass
  computation using log_softmax:
    log_probs = log_softmax(logits)
    probs = exp(log_probs)
    entropy = -(probs * log_probs).sum(-1)

  The original PufferLib entropy path computes:
    normalized_logits = logits - logsumexp(logits)    # = log_softmax
    probs = softmax(normalized_logits)                # redundant: exp of already-normalized logits
    entropy = -(normalized_logits * probs).sum(-1)

  The softmax call is redundant because probs = exp(log_softmax(logits)).
  The fused version replaces softmax(normalized_logits) with exp(normalized_logits),
  saving one full softmax pass over the 32 MB logit tensor.

Evidence standard: Gumbel-max is a high-confidence mathematical replacement
candidate (T1.2 accepted under reduced practical gate). Fused entropy is
numerically equivalent — it computes the same values via a more efficient path.
"""
from __future__ import annotations

import torch
import pufferlib.pytorch

_ACTION_ID_HEAD = 0
_TILE_X_HEAD = 1
_TILE_Y_HEAD = 2
_TILE_LEVEL_HEAD = 3
_VISIBLE_NPC_HEAD = 4
_PRAYER_HEAD = 5

_OBS_PLAYER_HP_CURRENT = 6
_OBS_PLAYER_HP_MAX = 7
_OBS_SHARK_COUNT = 22
_OBS_PRAYER_DOSE_COUNT = 23
_OBS_VISIBLE_TARGET_COUNT = 29


def _fused_entropy(normalized_logits: torch.Tensor) -> torch.Tensor:
    """Compute categorical entropy from log-softmax logits in a single pass.

    Input: normalized_logits = log_softmax(raw_logits), i.e. log(probs).
    Output: H = -sum(probs * log_probs) per category dimension.

    This replaces PufferLib's entropy() which calls softmax() on already-
    normalized logits — a redundant second pass over the full tensor.
    """
    min_real = torch.finfo(normalized_logits.dtype).min
    log_probs = torch.clamp(normalized_logits, min=min_real)
    probs = log_probs.exp()
    p_log_p = probs * log_probs
    return -p_log_p.sum(-1)


def sample_logits_gumbel(logits, action=None, observations=None, greedy: bool = False):
    """Drop-in replacement for pufferlib.pytorch.sample_logits.

    Both paths (sampling and log-prob recomputation) use fused entropy.
    The sampling path additionally uses Gumbel-max instead of multinomial.
    """
    if isinstance(logits, torch.distributions.Normal):
        return pufferlib.pytorch.sample_logits(logits, action)

    is_discrete = isinstance(logits, torch.Tensor)
    if not is_discrete and observations is not None:
        return _sample_structured_multidiscrete_logits(
            logits,
            observations=observations,
            action=action,
            greedy=greedy,
        )
    if is_discrete:
        logits_padded = logits.unsqueeze(0)
    else:
        logits_padded = torch.nn.utils.rnn.pad_sequence(
            [l.transpose(0, 1) for l in logits], batch_first=False, padding_value=-torch.inf
        ).permute(1, 2, 0)

    # Compute log-softmax once — used by log_prob, entropy, and (if sampling) Gumbel-max
    normalized_logits = logits_padded - logits_padded.logsumexp(dim=-1, keepdim=True)

    if action is None:
        if greedy:
            action = logits_padded.argmax(dim=-1).int()
        else:
            # --- Gumbel-max sampling (T1.1) ---
            uniform = torch.rand_like(logits_padded).clamp(1e-10, 1.0)
            gumbel_noise = -torch.log(-torch.log(uniform))
            action = (logits_padded + gumbel_noise).argmax(dim=-1).int()
    else:
        # --- Train-phase log-prob recomputation ---
        batch = logits_padded[0].shape[0]
        action = action.view(batch, -1).T

    assert len(logits_padded) == len(action)
    logprob = pufferlib.pytorch.log_prob(normalized_logits, action)

    # --- Fused entropy (T2.1): exp(log_probs) instead of redundant softmax ---
    logits_entropy = _fused_entropy(normalized_logits).sum(0)

    if is_discrete:
        return action.squeeze(0), logprob.squeeze(0), logits_entropy.squeeze(0)

    return action.T, logprob.sum(0), logits_entropy


def _sample_structured_multidiscrete_logits(
    logits: tuple[torch.Tensor, ...],
    *,
    observations: torch.Tensor,
    action: torch.Tensor | None,
    greedy: bool,
):
    if len(logits) != 6:
        raise ValueError(f"Expected 6 policy heads, got {len(logits)}.")

    batch_size = int(logits[0].shape[0])
    obs = _reshape_policy_observations(observations, batch_size=batch_size)
    visible_count = _observation_column(obs, _OBS_VISIBLE_TARGET_COUNT).clamp_min(0)
    shark_count = _observation_column(obs, _OBS_SHARK_COUNT).clamp_min(0)
    prayer_dose_count = _observation_column(obs, _OBS_PRAYER_DOSE_COUNT).clamp_min(0)

    action_id_logits = logits[_ACTION_ID_HEAD].clone()
    action_id_logits[shark_count <= 0, 4] = -torch.inf
    action_id_logits[prayer_dose_count <= 0, 5] = -torch.inf

    if action is None:
        action_id = _select_head_action(action_id_logits, greedy=greedy)
    else:
        action_view = action.view(batch_size, -1).to(device=logits[0].device)
        action_id = action_view[:, _ACTION_ID_HEAD].long()

    actions = torch.zeros((batch_size, len(logits)), device=logits[0].device, dtype=torch.int32)
    actions[:, _ACTION_ID_HEAD] = action_id.to(dtype=torch.int32)

    head_log_probs = torch.log_softmax(action_id_logits, dim=-1)
    logprob = head_log_probs.gather(-1, action_id.unsqueeze(-1)).squeeze(-1)
    entropy = _fused_entropy(head_log_probs)

    walk_mask = action_id == 1
    if walk_mask.any():
        for head_index in (_TILE_X_HEAD, _TILE_Y_HEAD, _TILE_LEVEL_HEAD):
            selected, selected_logprob, selected_entropy = _select_relevant_head(
                logits[head_index],
                action_view=action_view if action is not None else None,
                action_column=head_index,
                row_mask=walk_mask,
                greedy=greedy,
            )
            actions[walk_mask, head_index] = selected.to(dtype=torch.int32)
            logprob[walk_mask] += selected_logprob
            entropy[walk_mask] += selected_entropy

    attack_mask = action_id == 2
    if attack_mask.any():
        limits = torch.clamp(visible_count, min=1, max=logits[_VISIBLE_NPC_HEAD].shape[-1])
        visible_logits = _mask_head_by_limit(logits[_VISIBLE_NPC_HEAD], limits)
        selected, selected_logprob, selected_entropy = _select_relevant_head(
            visible_logits,
            action_view=action_view if action is not None else None,
            action_column=_VISIBLE_NPC_HEAD,
            row_mask=attack_mask,
            greedy=greedy,
        )
        actions[attack_mask, _VISIBLE_NPC_HEAD] = selected.to(dtype=torch.int32)
        logprob[attack_mask] += selected_logprob
        entropy[attack_mask] += selected_entropy

    prayer_mask = action_id == 3
    if prayer_mask.any():
        selected, selected_logprob, selected_entropy = _select_relevant_head(
            logits[_PRAYER_HEAD],
            action_view=action_view if action is not None else None,
            action_column=_PRAYER_HEAD,
            row_mask=prayer_mask,
            greedy=greedy,
        )
        actions[prayer_mask, _PRAYER_HEAD] = selected.to(dtype=torch.int32)
        logprob[prayer_mask] += selected_logprob
        entropy[prayer_mask] += selected_entropy

    return actions, logprob, entropy


def _select_relevant_head(
    head_logits: torch.Tensor,
    *,
    action_view: torch.Tensor | None,
    action_column: int,
    row_mask: torch.Tensor,
    greedy: bool,
) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
    masked_logits = head_logits[row_mask]
    if action_view is None:
        chosen = _select_head_action(masked_logits, greedy=greedy)
    else:
        chosen = action_view[row_mask, action_column].long()
    normalized = torch.log_softmax(masked_logits, dim=-1)
    logprob = normalized.gather(-1, chosen.unsqueeze(-1)).squeeze(-1)
    entropy = _fused_entropy(normalized)
    return chosen, logprob, entropy


def _select_head_action(head_logits: torch.Tensor, *, greedy: bool) -> torch.Tensor:
    if greedy:
        return head_logits.argmax(dim=-1).long()
    uniform = torch.rand_like(head_logits).clamp(1e-10, 1.0)
    gumbel_noise = -torch.log(-torch.log(uniform))
    return (head_logits + gumbel_noise).argmax(dim=-1).long()


def _mask_head_by_limit(head_logits: torch.Tensor, limits: torch.Tensor) -> torch.Tensor:
    indices = torch.arange(head_logits.shape[-1], device=head_logits.device).unsqueeze(0)
    masked = head_logits.clone()
    masked[indices >= limits.unsqueeze(-1)] = -torch.inf
    return masked


def _reshape_policy_observations(observations: torch.Tensor, *, batch_size: int) -> torch.Tensor:
    if observations.ndim == 1:
        return observations.view(1, -1)
    if observations.shape[0] == batch_size:
        return observations.reshape(batch_size, -1)
    return observations.reshape(batch_size, -1)


def _observation_column(observations: torch.Tensor, index: int) -> torch.Tensor:
    return observations[:, index].round().long()
