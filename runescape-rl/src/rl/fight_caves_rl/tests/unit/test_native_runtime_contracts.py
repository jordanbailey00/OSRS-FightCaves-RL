from fight_caves_rl.native_runtime import (
    ENV_BACKEND_NATIVE_PREVIEW,
    NATIVE_RUNTIME_ABI_VERSION,
    NATIVE_RUNTIME_ID,
    NATIVE_RUNTIME_MODE,
    NATIVE_RUNTIME_VERSION,
    expected_descriptor_bundle,
)


def test_expected_descriptor_bundle_is_derived_from_pr1_frozen_contracts():
    bundle = expected_descriptor_bundle()

    assert bundle["native_runtime_id"] == NATIVE_RUNTIME_ID
    assert bundle["native_runtime_version"] == NATIVE_RUNTIME_VERSION
    assert bundle["native_runtime_mode"] == NATIVE_RUNTIME_MODE
    assert bundle["abi_version"] == NATIVE_RUNTIME_ABI_VERSION
    assert bundle["descriptor_count"] == 5
    assert bundle["summary"]["action_schema_id"] == "headless_action_v1"
    assert bundle["summary"]["action_count"] == 7
    assert bundle["summary"]["flat_observation_feature_count"] == 134
    assert bundle["summary"]["reward_feature_count"] == 16
    assert bundle["summary"]["terminal_code_count"] == 6
    assert bundle["summary"]["episode_start_contract_id"] == "fight_cave_episode_start_v1"
    assert ENV_BACKEND_NATIVE_PREVIEW == "native_preview"
