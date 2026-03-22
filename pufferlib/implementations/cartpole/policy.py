"""RS-style MLP policy for CartPole.

Mirrors the runescape-rl MultiDiscreteMLPPolicy architecture:
- 2-layer GELU encoder with layer_init
- Separate action head(s) with std=0.01
- Value head with std=1.0

CartPole: obs=(4,) float32, action=Discrete(2).
Treated as MultiDiscrete([2]) for RS-style compatibility.
"""
from __future__ import annotations

import numpy as np
import torch
from torch import nn

import pufferlib.pytorch


class CartPoleRSPolicy(nn.Module):
    def __init__(self, env, hidden_size=128, **kwargs):
        super().__init__()
        self.hidden_size = hidden_size
        obs_size = int(np.prod(env.single_observation_space.shape))

        self.encoder = nn.Sequential(
            pufferlib.pytorch.layer_init(nn.Linear(obs_size, hidden_size)),
            nn.GELU(),
            pufferlib.pytorch.layer_init(nn.Linear(hidden_size, hidden_size)),
            nn.GELU(),
        )

        # Single action head for Discrete(2)
        self.action_head = pufferlib.pytorch.layer_init(
            nn.Linear(hidden_size, 2), std=0.01
        )
        self.value_head = pufferlib.pytorch.layer_init(
            nn.Linear(hidden_size, 1), std=1.0
        )

    def forward_eval(self, observations, state=None):
        hidden = self.encoder(observations.float().view(observations.shape[0], -1))
        logits = self.action_head(hidden)
        value = self.value_head(hidden)
        return logits, value, state

    def forward(self, observations, state=None):
        hidden = self.encoder(observations.float().view(observations.shape[0], -1))
        logits = self.action_head(hidden)
        value = self.value_head(hidden)
        return logits, value
