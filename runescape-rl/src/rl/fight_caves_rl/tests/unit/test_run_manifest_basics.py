from fight_caves_rl.manifests.run_manifest import build_bootstrap_manifest
from fight_caves_rl.utils.config import load_bootstrap_config
from fight_caves_rl.utils.paths import source_root


def test_bootstrap_manifest_contains_expected_fields():
    manifest = build_bootstrap_manifest(load_bootstrap_config({}))
    payload = manifest.to_dict()
    source = source_root()

    assert payload["rl_repo"] == str(source / "training-env" / "rl")
    assert payload["sim_repo"] == str(source / "training-env" / "sim")
    assert payload["rsps_repo"] == str(source / "rsps")
    assert payload["python_baseline"] == "3.11"
    assert payload["pufferlib_distribution"] == "pufferlib-core"
    assert payload["pufferlib_version"] == "3.0.17"
    assert payload["wandb_mode"] == "offline"
    assert payload["created_at"]
