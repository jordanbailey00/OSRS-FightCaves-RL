from __future__ import annotations

from pathlib import Path

import numpy as np

from fight_caves_rl.envs.observation_views import observation_remaining, observation_wave
from fight_caves_rl.native_runtime import (
    NativeEpisodeConfig,
    NativeRuntimeBuildConfig,
    NativeSlotDebugState,
    NativeTile,
    NativeVisibleNpcConfig,
    load_native_runtime,
)


def test_native_generic_attack_kill_advances_wave_and_completion_terminal(tmp_path: Path):
    with load_native_runtime(
        build_config=NativeRuntimeBuildConfig(output_dir=tmp_path / "build", force_rebuild=True)
    ) as runtime:
        runtime.reset_batch(
            [
                NativeEpisodeConfig(seed=11_001, start_wave=1),
                NativeEpisodeConfig(seed=63_063, start_wave=63),
            ]
        )
        runtime.configure_slot_visible_targets(
            0,
            [
                NativeVisibleNpcConfig(
                    npc_index=10,
                    id="tz_kih",
                    tile=NativeTile(1, 1, 0),
                    hitpoints_current=100,
                    hitpoints_max=100,
                )
            ],
        )
        runtime.configure_slot_visible_targets(
            1,
            [
                NativeVisibleNpcConfig(
                    npc_index=99,
                    id="tztok_jad",
                    tile=NativeTile(5, 5, 0),
                    hitpoints_current=100,
                    hitpoints_max=100,
                )
            ],
        )

        result = runtime.step_batch(
            [
                2, 0, 0, 0,
                2, 0, 0, 0,
            ]
        )

    np.testing.assert_array_equal(result.action_accepted, np.asarray([True, True], dtype=bool))
    np.testing.assert_array_equal(result.rejection_codes, np.zeros((2,), dtype=np.int32))
    assert observation_wave(result.flat_observations[0]) == 2
    assert observation_remaining(result.flat_observations[0]) == 2
    assert int(result.terminal_codes[0]) == 0
    assert bool(result.terminated[0]) is False

    assert int(result.terminal_codes[1]) == 2
    assert bool(result.terminated[1]) is True
    assert bool(result.truncated[1]) is False
    assert observation_wave(result.flat_observations[1]) == -1
    assert observation_remaining(result.flat_observations[1]) == -1


def test_native_generic_terminal_emission_covers_death_tick_cap_and_invalid_state(tmp_path: Path):
    with load_native_runtime(
        build_config=NativeRuntimeBuildConfig(output_dir=tmp_path / "build", force_rebuild=True)
    ) as runtime:
        runtime.reset_batch(
            [
                NativeEpisodeConfig(seed=20_001),
                NativeEpisodeConfig(seed=20_002),
                NativeEpisodeConfig(seed=20_003),
            ]
        )
        runtime.configure_slot_state(0, NativeSlotDebugState(hitpoints_current=20))
        runtime.configure_slot_visible_targets(
            0,
            [NativeVisibleNpcConfig(npc_index=1, id="tz_kih", tile=NativeTile(1, 1, 0))],
        )
        runtime.configure_slot_control(1, tick_cap=1)
        runtime.configure_slot_state(2, NativeSlotDebugState(hitpoints_current=701))

        result = runtime.step_batch(
            [
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
            ]
        )

    np.testing.assert_array_equal(
        result.terminal_codes,
        np.asarray([1, 3, 4], dtype=np.int32),
    )
    np.testing.assert_array_equal(
        result.terminated,
        np.asarray([True, False, True], dtype=bool),
    )
    np.testing.assert_array_equal(
        result.truncated,
        np.asarray([False, True, False], dtype=bool),
    )
