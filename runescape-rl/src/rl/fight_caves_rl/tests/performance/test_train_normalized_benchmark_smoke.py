from __future__ import annotations

import json
from pathlib import Path
import subprocess
import sys

from fight_caves_rl.utils.paths import repo_root


def test_train_normalized_benchmark_smoke(tmp_path: Path):
    output = tmp_path / "train_normalized.json"
    result = subprocess.run(
        [
            sys.executable,
            "scripts/benchmark_train_normalized.py",
            "--config",
            "configs/train/smoke_fast_v2.yaml",
            "--env-count",
            "1",
            "--measured-timesteps",
            "16",
            "--warmup-iterations",
            "1",
            "--output",
            str(output),
        ],
        cwd=str(repo_root()),
        text=True,
        capture_output=True,
        check=False,
    )
    assert result.returncode == 0, result.stderr
    payload = json.loads(output.read_text(encoding="utf-8"))
    assert str(payload["schema_id"]) == "train_benchmark_normalized_v1"
    assert int(payload["steady_state"]["measured_global_steps"]) >= 16
    assert float(payload["steady_state"]["training_sps"]) > 0.0
    assert "advantage_gae_seconds" in payload["time_breakdown"]
    assert "train_done_cleanup_seconds" in payload["time_breakdown"]
    assert "synchronization_stall_seconds" in payload["time_breakdown"]
