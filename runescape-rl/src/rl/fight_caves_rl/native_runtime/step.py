from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

JAD_HIT_RESOLVE_OUTCOME_BY_CODE = {
    0: "none",
    1: "pending",
    2: "protected",
    3: "hit",
}


@dataclass(frozen=True)
class NativeTile:
    x: int
    y: int
    level: int = 0


@dataclass(frozen=True)
class NativeVisibleNpcConfig:
    npc_index: int
    id: str
    tile: NativeTile
    hitpoints_current: int = 10
    hitpoints_max: int = 10
    listed: bool = True
    currently_visible: bool = True
    hidden: bool = False
    dead: bool = False
    under_attack: bool = False
    jad_telegraph_state: int = 0


@dataclass(frozen=True)
class NativeSlotDebugState:
    tile: NativeTile = field(default_factory=lambda: NativeTile(0, 0, 0))
    hitpoints_current: int = 700
    prayer_current: int = 43
    run_energy: int = 10000
    running: bool = True
    protect_from_magic: bool = False
    protect_from_missiles: bool = False
    protect_from_melee: bool = False
    attack_locked: bool = False
    food_locked: bool = False
    drink_locked: bool = False
    combo_locked: bool = False
    busy_locked: bool = False
    sharks: int = 20
    prayer_potion_dose_count: int = 32
    ammo: int = 1000


@dataclass(frozen=True)
class NativeStepBatchResult:
    env_count: int
    slot_indices: np.ndarray
    action_ids: np.ndarray
    action_accepted: np.ndarray
    rejection_codes: np.ndarray
    resolved_target_npc_indices: np.ndarray
    jad_telegraph_states: np.ndarray
    jad_hit_resolve_outcome_codes: np.ndarray
    jad_hit_resolve_outcomes: tuple[str, ...]
    flat_observations: np.ndarray
    reward_features: np.ndarray
    terminal_codes: np.ndarray
    terminated: np.ndarray
    truncated: np.ndarray
    episode_ticks: np.ndarray
    episode_steps: np.ndarray
