from __future__ import annotations

import torch

from fight_caves_rl.puffer.gumbel_sample import sample_logits_gumbel


def _policy_observation(
    *,
    sharks: int = 20,
    prayer_doses: int = 32,
    visible_targets: int = 1,
) -> torch.Tensor:
    observation = torch.zeros((1, 134), dtype=torch.float32)
    observation[0, 22] = float(sharks)
    observation[0, 23] = float(prayer_doses)
    observation[0, 29] = float(visible_targets)
    return observation


def test_structured_sampler_masks_attack_target_to_visible_range():
    logits = (
        torch.tensor([[0.0, 0.0, 10.0, 0.0, 0.0, 0.0, 0.0]], dtype=torch.float32),
        torch.zeros((1, 16_384), dtype=torch.float32),
        torch.zeros((1, 16_384), dtype=torch.float32),
        torch.zeros((1, 4), dtype=torch.float32),
        torch.tensor([[0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 10.0]], dtype=torch.float32),
        torch.zeros((1, 3), dtype=torch.float32),
    )

    action, _logprob, _entropy = sample_logits_gumbel(
        logits,
        observations=_policy_observation(visible_targets=1),
        greedy=True,
    )

    assert action.tolist() == [[2, 0, 0, 0, 0, 0]]


def test_structured_sampler_masks_unavailable_consumables_from_action_id():
    logits = (
        torch.tensor([[0.0, 0.0, 0.0, 0.0, 9.0, 8.0, 7.0]], dtype=torch.float32),
        torch.zeros((1, 16_384), dtype=torch.float32),
        torch.zeros((1, 16_384), dtype=torch.float32),
        torch.zeros((1, 4), dtype=torch.float32),
        torch.zeros((1, 8), dtype=torch.float32),
        torch.zeros((1, 3), dtype=torch.float32),
    )

    action, _logprob, _entropy = sample_logits_gumbel(
        logits,
        observations=_policy_observation(sharks=0, prayer_doses=0, visible_targets=0),
        greedy=True,
    )

    assert int(action[0, 0].item()) == 6
    assert action.tolist() == [[6, 0, 0, 0, 0, 0]]
