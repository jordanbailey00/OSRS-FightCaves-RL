"""RS-style MLP policy for Boids.

Boids: obs=(num_boids*4,) float32, action=MultiDiscrete([5,5]).
This is the key comparison env because runescape-rl uses MultiDiscrete
action spaces. The RS-style policy uses separate action heads per
dimension, matching the runescape-rl architecture exactly.

Architecture differences from stock Boids policy (ocean/torch.py):
- Stock: max-pools over boid embeddings, single concat action head
- RS-style: flat MLP encoder, separate action heads per dimension
"""
from __future__ import annotations

import numpy as np
import torch
from torch import nn

import pufferlib.pytorch


class BoidsRSPolicy(nn.Module):
    """RS-style MultiDiscrete policy with separate action heads."""

    def __init__(self, env, hidden_size=128, **kwargs):
        super().__init__()
        self.hidden_size = hidden_size
        obs_size = int(np.prod(env.single_observation_space.shape))
        self.action_nvec = tuple(int(n) for n in env.single_action_space.nvec)

        self.encoder = nn.Sequential(
            pufferlib.pytorch.layer_init(nn.Linear(obs_size, hidden_size)),
            nn.GELU(),
            pufferlib.pytorch.layer_init(nn.Linear(hidden_size, hidden_size)),
            nn.GELU(),
        )

        # Separate action heads — mirrors runescape-rl's MultiDiscreteMLPPolicy
        self.action_heads = nn.ModuleList(
            pufferlib.pytorch.layer_init(nn.Linear(hidden_size, n), std=0.01)
            for n in self.action_nvec
        )
        self.value_head = pufferlib.pytorch.layer_init(
            nn.Linear(hidden_size, 1), std=1.0
        )

    def forward_eval(self, observations, state=None):
        hidden = self.encoder(observations.float().view(observations.shape[0], -1))
        logits = tuple(head(hidden) for head in self.action_heads)
        value = self.value_head(hidden)
        return logits, value, state

    def forward(self, observations, state=None):
        hidden = self.encoder(observations.float().view(observations.shape[0], -1))
        logits = tuple(head(hidden) for head in self.action_heads)
        value = self.value_head(hidden)
        return logits, value
