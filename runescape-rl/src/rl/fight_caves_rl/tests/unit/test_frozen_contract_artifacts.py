from fight_caves_rl.contracts.frozen_artifacts import contract_artifact_drift


def test_frozen_contract_artifacts_match_live_registry():
    drift = contract_artifact_drift()
    assert drift == []
