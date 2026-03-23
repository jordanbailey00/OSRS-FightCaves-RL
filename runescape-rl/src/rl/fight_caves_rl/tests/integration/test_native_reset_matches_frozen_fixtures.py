from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pytest

from fight_caves_rl.envs.puffer_encoding import encode_observation_for_policy
from fight_caves_rl.fixtures.golden_fixtures import GOLDEN_FIXTURES_DIR
from fight_caves_rl.native_runtime import (
    NativeEpisodeConfig,
    NativeRuntimeBuildConfig,
    load_native_runtime,
)


RESET_FIXTURE_PATHS = (
    GOLDEN_FIXTURES_DIR / "reset" / "seed_11001_wave_1.json",
    GOLDEN_FIXTURES_DIR / "reset" / "seed_63063_wave_63.json",
)


@pytest.mark.parametrize("fixture_path", RESET_FIXTURE_PATHS)
def test_native_reset_matches_frozen_reset_fixture(
    tmp_path: Path,
    fixture_path: Path,
):
    fixture = json.loads(fixture_path.read_text(encoding="utf-8"))
    params = fixture["metadata"]["parameters"]
    payload = fixture["payload"]

    with load_native_runtime(
        build_config=NativeRuntimeBuildConfig(output_dir=tmp_path / "build", force_rebuild=True)
    ) as runtime:
        result = runtime.reset_batch(
            [
                NativeEpisodeConfig(
                    seed=int(params["seed"]),
                    start_wave=int(params["start_wave"]),
                    ammo=int(params["ammo"]),
                    prayer_potions=int(params["prayer_potions"]),
                    sharks=int(params["sharks"]),
                )
            ]
        )

    assert result.env_count == 1
    assert result.slot_indices.tolist() == [0]
    assert result.episode_states[0] == payload["first_episode_state"]
    assert result.observations[0] == payload["first_observation"]
    np.testing.assert_allclose(
        result.flat_observations[0],
        encode_observation_for_policy(result.observations[0]),
    )
    np.testing.assert_array_equal(result.reward_features, np.zeros((1, 16), dtype=np.float32))
    np.testing.assert_array_equal(result.terminal_codes, np.zeros((1,), dtype=np.int32))
    np.testing.assert_array_equal(result.terminated, np.zeros((1,), dtype=bool))
    np.testing.assert_array_equal(result.truncated, np.zeros((1,), dtype=bool))
    np.testing.assert_array_equal(result.episode_ticks, np.zeros((1,), dtype=np.int32))
    np.testing.assert_array_equal(result.episode_steps, np.zeros((1,), dtype=np.int32))


def test_native_reset_batch_supports_multiple_slots(tmp_path: Path):
    fixtures = [
        json.loads(path.read_text(encoding="utf-8"))
        for path in RESET_FIXTURE_PATHS
    ]

    with load_native_runtime(
        build_config=NativeRuntimeBuildConfig(output_dir=tmp_path / "build", force_rebuild=True)
    ) as runtime:
        result = runtime.reset_batch(
            [
                NativeEpisodeConfig(**fixture["metadata"]["parameters"])
                for fixture in fixtures
            ]
        )

    assert result.env_count == 2
    assert result.slot_indices.tolist() == [0, 1]
    for index, fixture in enumerate(fixtures):
        payload = fixture["payload"]
        assert result.episode_states[index] == payload["first_episode_state"]
        assert result.observations[index] == payload["first_observation"]
        np.testing.assert_allclose(
            result.flat_observations[index],
            encode_observation_for_policy(result.observations[index]),
        )
    np.testing.assert_array_equal(result.reward_features, np.zeros((2, 16), dtype=np.float32))
    np.testing.assert_array_equal(result.terminal_codes, np.zeros((2,), dtype=np.int32))


def test_native_reset_is_deterministic_for_same_seed_and_config(tmp_path: Path):
    config = NativeEpisodeConfig(seed=11_001, start_wave=1, ammo=1000, prayer_potions=8, sharks=20)

    with load_native_runtime(
        build_config=NativeRuntimeBuildConfig(output_dir=tmp_path / "build", force_rebuild=True)
    ) as runtime:
        first = runtime.reset_batch([config])
        second = runtime.reset_batch([config])

    assert first.episode_states == second.episode_states
    assert first.observations == second.observations
    np.testing.assert_array_equal(first.flat_observations, second.flat_observations)
    np.testing.assert_array_equal(first.reward_features, second.reward_features)
