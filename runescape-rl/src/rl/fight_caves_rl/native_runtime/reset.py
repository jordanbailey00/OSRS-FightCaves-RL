from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import numpy as np

from fight_caves_rl.envs.schema import (
    HEADLESS_OBSERVATION_COMPATIBILITY_POLICY,
    HEADLESS_OBSERVATION_SCHEMA,
)


@dataclass(frozen=True)
class NativeEpisodeConfig:
    seed: int
    start_wave: int = 1
    ammo: int = 1000
    prayer_potions: int = 8
    sharks: int = 20


@dataclass(frozen=True)
class NativeResetSlotState:
    seed: int
    wave: int
    rotation: int
    remaining: int
    ammo: int
    prayer_potion_dose_count: int
    sharks: int
    player_tile_x: int = 0
    player_tile_y: int = 0
    player_tile_level: int = 0

    def to_episode_state(self) -> dict[str, Any]:
        return {
            "seed": self.seed,
            "wave": self.wave,
            "rotation": self.rotation,
            "remaining": self.remaining,
            "player_tile": {
                "x": self.player_tile_x,
                "y": self.player_tile_y,
                "level": self.player_tile_level,
            },
        }

    def to_observation(self) -> dict[str, Any]:
        return {
            "schema_id": HEADLESS_OBSERVATION_SCHEMA.contract_id,
            "schema_version": HEADLESS_OBSERVATION_SCHEMA.version,
            "compatibility_policy": HEADLESS_OBSERVATION_COMPATIBILITY_POLICY,
            "tick": 0,
            "episode_seed": self.seed,
            "player": {
                "tile": {
                    "x": self.player_tile_x,
                    "y": self.player_tile_y,
                    "level": self.player_tile_level,
                },
                "hitpoints_current": 700,
                "hitpoints_max": 700,
                "prayer_current": 43,
                "prayer_max": 43,
                "run_energy": 10000,
                "run_energy_max": 10000,
                "run_energy_percent": 100,
                "running": True,
                "protection_prayers": {
                    "protect_from_magic": False,
                    "protect_from_missiles": False,
                    "protect_from_melee": False,
                },
                "lockouts": {
                    "attack_locked": False,
                    "food_locked": False,
                    "drink_locked": False,
                    "combo_locked": False,
                    "busy_locked": False,
                },
                "consumables": {
                    "shark_count": self.sharks,
                    "prayer_potion_dose_count": self.prayer_potion_dose_count,
                    "ammo_id": "adamant_bolts",
                    "ammo_count": self.ammo,
                },
            },
            "wave": {
                "wave": self.wave,
                "rotation": self.rotation,
                "remaining": self.remaining,
            },
            "npcs": [],
        }


@dataclass(frozen=True)
class NativeResetBatchResult:
    env_count: int
    slot_indices: np.ndarray
    flat_observations: np.ndarray
    reward_features: np.ndarray
    terminal_codes: np.ndarray
    terminated: np.ndarray
    truncated: np.ndarray
    episode_ticks: np.ndarray
    episode_steps: np.ndarray
    episode_states: tuple[dict[str, Any], ...]
    observations: tuple[dict[str, Any], ...]
