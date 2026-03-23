from __future__ import annotations

from fight_caves_rl.envs.correctness_env import FightCavesCorrectnessEnv
from fight_caves_rl.replay.trace_packs import (
    project_episode_state_for_determinism,
    project_observation_for_determinism,
)


def collect_reset_repro(
    seed: int,
    *,
    start_wave: int = 1,
    ammo: int = 1000,
    prayer_potions: int = 8,
    sharks: int = 20,
) -> dict[str, object]:
    env = FightCavesCorrectnessEnv()
    try:
        reset_options = {
            "start_wave": int(start_wave),
            "ammo": int(ammo),
            "prayer_potions": int(prayer_potions),
            "sharks": int(sharks),
        }
        first_observation, first_info = env.reset(seed=seed, options=reset_options)
        for _ in range(3):
            env.step(0)
        second_observation, second_info = env.reset(seed=seed, options=reset_options)
        return {
            "seed": seed,
            "start_wave": int(start_wave),
            "ammo": int(ammo),
            "prayer_potions": int(prayer_potions),
            "sharks": int(sharks),
            "first_episode_state": project_episode_state_for_determinism(first_info["episode_state"]),
            "second_episode_state": project_episode_state_for_determinism(second_info["episode_state"]),
            "first_observation": project_observation_for_determinism(
                first_observation,
                episode_start_tick=int(first_observation["tick"]),
                episode_start_tile=first_observation["player"]["tile"],
            ),
            "second_observation": project_observation_for_determinism(
                second_observation,
                episode_start_tick=int(second_observation["tick"]),
                episode_start_tile=second_observation["player"]["tile"],
            ),
        }
    finally:
        env.close()
