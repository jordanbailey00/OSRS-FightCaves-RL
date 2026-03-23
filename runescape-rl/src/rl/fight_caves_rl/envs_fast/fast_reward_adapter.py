from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from fight_caves_rl.contracts.reward_feature_schema import (
    REWARD_FEATURE_INDEX,
    REWARD_FEATURE_NAMES,
)
from fight_caves_rl.rewards.registry import RewardConfig, load_reward_config

_REWARD_COEFFICIENT_TO_FEATURE = {
    "wave_progress": "wave_clear",
    "cave_complete": "cave_complete",
    "player_death": "player_death",
    "npc_damage": "damage_dealt",
    "player_damage": "damage_taken",
    "shark_use": "food_used",
    "prayer_potion_use": "prayer_potion_used",
    "step_penalty": "tick_penalty_base",
}

# Observation-based shaping coefficients — computed from obs, not JVM reward features.
_OBSERVATION_SHAPING_COEFFICIENTS = frozenset({"proximity_approach"})

# Observation layout constants for proximity computation.
_PLAYER_X_IDX = 3
_PLAYER_Y_IDX = 4
_NPC_BASE = 30
_NPC_STRIDE = 13
_NPC_PRESENT_OFF = 0
_NPC_TILE_X_OFF = 4
_NPC_TILE_Y_OFF = 5
_NPC_HP_OFF = 7
_NPC_DEAD_OFF = 10
_MAX_NPCS = 8


@dataclass(frozen=True)
class FastRewardAdapter:
    config_id: str
    mode: str
    weights: np.ndarray
    unsupported_coefficients: tuple[str, ...]
    proximity_approach_coeff: float

    @classmethod
    def from_config_id(cls, config_id: str) -> "FastRewardAdapter":
        return cls.from_config(load_reward_config(config_id))

    @classmethod
    def from_config(cls, config: RewardConfig) -> "FastRewardAdapter":
        weights = np.zeros((len(REWARD_FEATURE_NAMES),), dtype=np.float32)
        unsupported: list[str] = []
        proximity_approach_coeff = 0.0
        for coefficient_name, coefficient_value in config.coefficients.items():
            if coefficient_name in _OBSERVATION_SHAPING_COEFFICIENTS:
                if coefficient_name == "proximity_approach":
                    proximity_approach_coeff = float(coefficient_value)
                continue
            feature_name = _resolve_feature_name(str(coefficient_name))
            if feature_name is None:
                if float(coefficient_value) != 0.0:
                    unsupported.append(str(coefficient_name))
                continue
            weights[REWARD_FEATURE_INDEX[feature_name]] += np.float32(coefficient_value)
        return cls(
            config_id=config.config_id,
            mode=config.mode,
            weights=weights,
            unsupported_coefficients=tuple(sorted(unsupported)),
            proximity_approach_coeff=proximity_approach_coeff,
        )

    @property
    def has_proximity_shaping(self) -> bool:
        return self.proximity_approach_coeff != 0.0

    def validate_supported(self) -> None:
        if self.unsupported_coefficients:
            joined = ", ".join(self.unsupported_coefficients)
            raise ValueError(
                "The current V2 fast reward adapter cannot reconstruct unsupported "
                f"coefficients in Python: {joined}. Use V2 reward configs that map "
                "directly onto emitted reward features."
            )

    def weight_batch(self, reward_features: np.ndarray) -> np.ndarray:
        self.validate_supported()
        batch = np.asarray(reward_features, dtype=np.float32)
        if batch.ndim != 2 or batch.shape[1] != self.weights.shape[0]:
            raise ValueError(
                "Fast reward feature layout drift: "
                f"expected (*, {self.weights.shape[0]}), got {batch.shape}."
            )
        return (batch @ self.weights).astype(np.float32, copy=False)

    def proximity_shaping_batch(
        self,
        prev_observations: np.ndarray,
        curr_observations: np.ndarray,
    ) -> np.ndarray:
        """Compute proximity approach shaping from observation deltas.

        Returns per-env reward bonus: positive when player moved closer to
        nearest alive NPC (Chebyshev distance decrease), negative when retreating.
        Zero when no alive NPCs exist in either prev or curr observation.
        """
        prev_dist = _min_npc_chebyshev_batch(prev_observations)
        curr_dist = _min_npc_chebyshev_batch(curr_observations)
        # Only apply when both steps had alive NPCs visible.
        has_signal = (prev_dist > 0.5) & (curr_dist > 0.5)
        delta = prev_dist - curr_dist  # positive when approaching
        return np.where(has_signal, delta * self.proximity_approach_coeff, 0.0).astype(
            np.float32, copy=False,
        )


def _min_npc_chebyshev_batch(obs: np.ndarray) -> np.ndarray:
    """Return (N,) array of min Chebyshev distance to nearest alive visible NPC."""
    player_x = obs[:, _PLAYER_X_IDX]
    player_y = obs[:, _PLAYER_Y_IDX]
    inf = np.float32(1e6)
    min_dist = np.full(obs.shape[0], inf, dtype=np.float32)
    for i in range(_MAX_NPCS):
        base = _NPC_BASE + i * _NPC_STRIDE
        present = obs[:, base + _NPC_PRESENT_OFF]
        hp = obs[:, base + _NPC_HP_OFF]
        dead = obs[:, base + _NPC_DEAD_OFF]
        alive = (present > 0.5) & (hp > 0.5) & (dead < 0.5)
        dist = np.maximum(
            np.abs(player_x - obs[:, base + _NPC_TILE_X_OFF]),
            np.abs(player_y - obs[:, base + _NPC_TILE_Y_OFF]),
        )
        dist_or_inf = np.where(alive, dist, inf)
        np.minimum(min_dist, dist_or_inf, out=min_dist)
    return np.where(min_dist < inf, min_dist, np.float32(0.0))


def _resolve_feature_name(coefficient_name: str) -> str | None:
    if coefficient_name in REWARD_FEATURE_INDEX:
        return coefficient_name
    return _REWARD_COEFFICIENT_TO_FEATURE.get(coefficient_name)
