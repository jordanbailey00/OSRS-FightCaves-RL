from __future__ import annotations

from pathlib import Path

import numpy as np

from fight_caves_rl.native_runtime import (
    NativeEpisodeConfig,
    NativeRuntimeBuildConfig,
    NativeSlotDebugState,
    NativeTile,
    NativeVisibleNpcConfig,
    load_native_runtime,
)


def test_native_step_supports_seven_actions_and_visible_target_ordering(tmp_path: Path):
    with load_native_runtime(
        build_config=NativeRuntimeBuildConfig(output_dir=tmp_path / "build", force_rebuild=True)
    ) as runtime:
        runtime.reset_batch([NativeEpisodeConfig(seed=10_001 + index) for index in range(7)])
        runtime.configure_slot_visible_targets(
            2,
            [
                NativeVisibleNpcConfig(
                    npc_index=41,
                    id="tok_xil",
                    tile=NativeTile(5, 5, 0),
                    hitpoints_current=200,
                    hitpoints_max=200,
                ),
                NativeVisibleNpcConfig(
                    npc_index=40,
                    id="tz_kih",
                    tile=NativeTile(1, 1, 0),
                    hitpoints_current=200,
                    hitpoints_max=200,
                ),
            ],
        )

        result = runtime.step_batch(
            [
                0, 0, 0, 0,
                1, 5, 6, 0,
                2, 0, 0, 0,
                3, 1, 0, 0,
                4, 0, 0, 0,
                5, 0, 0, 0,
                6, 0, 0, 0,
            ]
        )

    assert result.env_count == 7
    np.testing.assert_array_equal(result.action_accepted, np.ones((7,), dtype=bool))
    np.testing.assert_array_equal(result.rejection_codes, np.zeros((7,), dtype=np.int32))
    np.testing.assert_array_equal(result.terminal_codes, np.zeros((7,), dtype=np.int32))
    assert result.reward_features.shape == (7, 16)
    np.testing.assert_array_equal(result.episode_ticks, np.ones((7,), dtype=np.int32))
    np.testing.assert_array_equal(result.episode_steps, np.ones((7,), dtype=np.int32))

    walk_row = result.flat_observations[1]
    assert walk_row[3:6].tolist() == [5.0, 6.0, 0.0]

    attack_row = result.flat_observations[2]
    assert int(result.resolved_target_npc_indices[2]) == 40
    assert int(attack_row[29]) == 2
    assert attack_row[30:34].tolist() == [1.0, 0.0, 40.0, 0.0]
    assert attack_row[43:47].tolist() == [1.0, 1.0, 41.0, 5.0]

    prayer_row = result.flat_observations[3]
    assert prayer_row[14:17].tolist() == [0.0, 1.0, 0.0]

    eat_row = result.flat_observations[4]
    assert int(eat_row[22]) == 19

    drink_row = result.flat_observations[5]
    assert int(drink_row[23]) == 28

    run_row = result.flat_observations[6]
    assert int(run_row[13]) == 0


def test_native_step_rejections_cover_pr4_control_surface_only(tmp_path: Path):
    with load_native_runtime(
        build_config=NativeRuntimeBuildConfig(output_dir=tmp_path / "build", force_rebuild=True)
    ) as runtime:
        runtime.reset_batch([NativeEpisodeConfig(seed=20_001 + index) for index in range(8)])

        runtime.configure_slot_visible_targets(
            0,
            [NativeVisibleNpcConfig(npc_index=90, id="tz_kih", tile=NativeTile(1, 1, 0))],
        )
        runtime.configure_slot_visible_targets(
            1,
            [
                NativeVisibleNpcConfig(
                    npc_index=91,
                    id="tz_kih",
                    tile=NativeTile(1, 1, 0),
                    currently_visible=False,
                )
            ],
        )
        runtime.configure_slot_visible_targets(
            2,
            [NativeVisibleNpcConfig(npc_index=92, id="tz_kih", tile=NativeTile(1, 1, 0))],
        )
        runtime.configure_slot_state(2, NativeSlotDebugState(busy_locked=True))
        runtime.configure_slot_state(3, NativeSlotDebugState(sharks=0))
        runtime.configure_slot_state(
            4,
            NativeSlotDebugState(drink_locked=True),
        )
        runtime.configure_slot_state(
            5,
            NativeSlotDebugState(run_energy=0, running=False),
        )

        rejection_batch = runtime.step_batch(
            [
                2, 99, 0, 0,
                2, 0, 0, 0,
                2, 0, 0, 0,
                4, 0, 0, 0,
                5, 0, 0, 0,
                6, 0, 0, 0,
                1, 0, 0, 0,
            ],
            slot_indices=[0, 1, 2, 3, 4, 5, 6],
        )

        duplicate_batch = runtime.step_batch(
            [
                0, 0, 0, 0,
                0, 0, 0, 0,
            ],
            slot_indices=[7, 7],
        )

    np.testing.assert_array_equal(
        rejection_batch.rejection_codes,
        np.asarray([2, 3, 4, 5, 6, 8, 9], dtype=np.int32),
    )
    np.testing.assert_array_equal(
        rejection_batch.action_accepted,
        np.zeros((7,), dtype=bool),
    )
    np.testing.assert_array_equal(rejection_batch.terminal_codes, np.zeros((7,), dtype=np.int32))
    assert rejection_batch.reward_features.shape == (7, 16)
    np.testing.assert_array_equal(
        duplicate_batch.rejection_codes,
        np.asarray([0, 1], dtype=np.int32),
    )
    np.testing.assert_array_equal(
        duplicate_batch.action_accepted,
        np.asarray([True, False], dtype=bool),
    )
