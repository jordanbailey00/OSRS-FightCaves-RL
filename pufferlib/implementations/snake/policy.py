"""RS-style MLP policy for Snake.

Snake: obs=(11,11) int8, action=Discrete(4).
Multi-agent env (256 agents per env instance by default = 4096+ total agents).
The 2D observation requires flattening, and the int8 dtype needs float conversion.
Tests batch processing at scale with many concurrent agents.
"""
from __future__ import annotations

import numpy as np
import torch
from torch import nn

import pufferlib.pytorch


class SnakeRSPolicy(nn.Module):
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

        self.action_head = pufferlib.pytorch.layer_init(
            nn.Linear(hidden_size, 4), std=0.01
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
