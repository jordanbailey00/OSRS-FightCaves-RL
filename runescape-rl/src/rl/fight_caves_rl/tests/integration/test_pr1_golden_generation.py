import pytest

from fight_caves_rl.tests.smoke._helpers import require_live_runtime, run_script

pytestmark = pytest.mark.usefixtures("disable_subprocess_capture")


def test_export_frozen_contracts_check_passes():
    result = run_script("export_frozen_contracts.py", "--check")
    assert result.returncode == 0, result.stderr


def test_generate_pr1_golden_fixtures_check_passes():
    require_live_runtime()

    result = run_script(
        "generate_pr1_golden_fixtures.py",
        "--check",
        timeout_seconds=600.0,
    )
    assert result.returncode == 0, result.stderr
