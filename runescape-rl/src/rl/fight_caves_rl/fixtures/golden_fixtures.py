from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
from typing import Any

from fight_caves_rl.contracts.mechanics_contract import FIGHT_CAVES_V2_MECHANICS_CONTRACT
from fight_caves_rl.contracts.parity_trace_schema import MECHANICS_PARITY_TRACE_SCHEMA
from fight_caves_rl.contracts.reward_feature_schema import REWARD_FEATURE_SCHEMA
from fight_caves_rl.contracts.terminal_codes import TERMINAL_CODE_SCHEMA
from fight_caves_rl.envs.schema import (
    FIGHT_CAVE_EPISODE_START_CONTRACT,
    HEADLESS_ACTION_SCHEMA,
    HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA,
)
from fight_caves_rl.fixtures.reset_repro import collect_reset_repro
from fight_caves_rl.replay.mechanics_parity import (
    ORACLE_RUNTIME_PATH_ID,
)


GOLDEN_FIXTURES_DIR = Path(__file__).resolve().parent / "goldens"


@dataclass(frozen=True)
class ResetFixtureConfig:
    fixture_path: str
    seed: int
    start_wave: int
    ammo: int = 1000
    prayer_potions: int = 8
    sharks: int = 20


@dataclass(frozen=True)
class TraceFixtureConfig:
    fixture_path: str
    generator_kind: str
    trace_pack: str | None = None
    scenario_id: str | None = None
    seed: int | None = None
    tick_cap: int = 20_000


FIXTURE_CONFIGS: tuple[ResetFixtureConfig | TraceFixtureConfig, ...] = (
    ResetFixtureConfig("reset/seed_11001_wave_1.json", seed=11_001, start_wave=1),
    ResetFixtureConfig("reset/seed_63063_wave_63.json", seed=63_063, start_wave=63),
    TraceFixtureConfig(
        "parity/oracle_single_wave.json",
        generator_kind="trace_pack",
        trace_pack="parity_single_wave_v0",
        seed=11_001,
    ),
    TraceFixtureConfig(
        "parity/oracle_full_run.json",
        generator_kind="scenario",
        scenario_id="full_run",
    ),
    TraceFixtureConfig(
        "parity/oracle_jad_telegraph.json",
        generator_kind="scenario",
        scenario_id="jad_telegraph",
    ),
    TraceFixtureConfig(
        "parity/oracle_jad_prayer_protected.json",
        generator_kind="scenario",
        scenario_id="jad_prayer_protected",
    ),
    TraceFixtureConfig(
        "parity/oracle_jad_prayer_too_late.json",
        generator_kind="scenario",
        scenario_id="jad_prayer_too_late",
    ),
    TraceFixtureConfig(
        "parity/oracle_jad_healer.json",
        generator_kind="scenario",
        scenario_id="jad_healer",
    ),
    TraceFixtureConfig(
        "parity/oracle_tzkek_split.json",
        generator_kind="scenario",
        scenario_id="tzkek_split",
    ),
)


def generate_all_fixtures(output_dir: Path = GOLDEN_FIXTURES_DIR) -> list[Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    for config in FIXTURE_CONFIGS:
        payload = _build_fixture_payload(config)
        path = output_dir / config.fixture_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        written.append(path)
    return written


def fixture_drift(output_dir: Path = GOLDEN_FIXTURES_DIR) -> list[str]:
    errors: list[str] = []
    for config in FIXTURE_CONFIGS:
        expected = _build_fixture_payload(config)
        path = output_dir / config.fixture_path
        if not path.is_file():
            errors.append(f"Missing golden fixture: {path}")
            continue
        current = json.loads(path.read_text(encoding="utf-8"))
        if current != expected:
            errors.append(
                f"Golden fixture drifted for {path}. Regenerate it with scripts/generate_pr1_golden_fixtures.py."
            )
    return errors


def _build_fixture_payload(config: ResetFixtureConfig | TraceFixtureConfig) -> dict[str, Any]:
    if isinstance(config, ResetFixtureConfig):
        return _build_reset_fixture(config)
    return _build_trace_fixture(config)


def _build_reset_fixture(config: ResetFixtureConfig) -> dict[str, Any]:
    payload = _run_json_script(
        "collect_reset_repro.py",
        "--seed",
        str(config.seed),
        "--start-wave",
        str(config.start_wave),
        "--ammo",
        str(config.ammo),
        "--prayer-potions",
        str(config.prayer_potions),
        "--sharks",
        str(config.sharks),
    )
    metadata = {
        "fixture_type": "reset_fixture",
        "source_runtime": "wrapper_headless_debug_client",
        "source_version": _source_version_identifier(),
        "source_commit": _source_commit(),
        "generator_script": "scripts/generate_pr1_golden_fixtures.py",
        "schema_versions": {
            "action_schema_id": HEADLESS_ACTION_SCHEMA.contract_id,
            "action_schema_version": HEADLESS_ACTION_SCHEMA.version,
            "flat_observation_schema_id": HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA.identity.contract_id,
            "flat_observation_schema_version": HEADLESS_TRAINING_FLAT_OBSERVATION_SCHEMA.identity.version,
            "episode_start_contract_id": FIGHT_CAVE_EPISODE_START_CONTRACT.identity.contract_id,
            "episode_start_contract_version": FIGHT_CAVE_EPISODE_START_CONTRACT.identity.version,
        },
        "parameters": {
            "seed": config.seed,
            "start_wave": config.start_wave,
            "ammo": config.ammo,
            "prayer_potions": config.prayer_potions,
            "sharks": config.sharks,
        },
    }
    return _with_integrity(metadata=metadata, payload=payload)


def _build_trace_fixture(config: TraceFixtureConfig) -> dict[str, Any]:
    if config.generator_kind == "trace_pack":
        assert config.trace_pack is not None
        assert config.seed is not None
        payload = _run_json_script(
            "collect_mechanics_parity_trace.py",
            "--mode",
            ORACLE_RUNTIME_PATH_ID,
            "--trace-pack",
            config.trace_pack,
            "--seed",
            str(config.seed),
            "--tick-cap",
            str(config.tick_cap),
        )
        parameters = {
            "trace_pack": config.trace_pack,
            "seed": config.seed,
            "tick_cap": config.tick_cap,
        }
    elif config.generator_kind == "scenario":
        assert config.scenario_id is not None
        payload = _run_json_script(
            "collect_named_parity_scenario.py",
            "--mode",
            ORACLE_RUNTIME_PATH_ID,
            "--scenario-id",
            config.scenario_id,
        )
        parameters = {
            "scenario_id": config.scenario_id,
        }
    else:
        raise ValueError(f"Unsupported fixture generator kind: {config.generator_kind!r}")

    metadata = {
        "fixture_type": "mechanics_parity_trace",
        "source_runtime": ORACLE_RUNTIME_PATH_ID,
        "source_version": _source_version_identifier(),
        "source_commit": _source_commit(),
        "generator_script": "scripts/generate_pr1_golden_fixtures.py",
        "schema_versions": {
            "mechanics_contract_id": FIGHT_CAVES_V2_MECHANICS_CONTRACT.identity.contract_id,
            "mechanics_contract_version": FIGHT_CAVES_V2_MECHANICS_CONTRACT.identity.version,
            "action_schema_id": HEADLESS_ACTION_SCHEMA.contract_id,
            "action_schema_version": HEADLESS_ACTION_SCHEMA.version,
            "parity_trace_schema_id": MECHANICS_PARITY_TRACE_SCHEMA.contract_id,
            "parity_trace_schema_version": MECHANICS_PARITY_TRACE_SCHEMA.version,
            "reward_feature_schema_id": REWARD_FEATURE_SCHEMA.contract_id,
            "reward_feature_schema_version": REWARD_FEATURE_SCHEMA.version,
            "terminal_code_schema_id": TERMINAL_CODE_SCHEMA.contract_id,
            "terminal_code_schema_version": TERMINAL_CODE_SCHEMA.version,
        },
        "parameters": parameters,
    }
    return _with_integrity(metadata=metadata, payload=payload)


def _with_integrity(*, metadata: dict[str, Any], payload: dict[str, Any]) -> dict[str, Any]:
    envelope = {
        "metadata": metadata,
        "payload": payload,
    }
    checksum = hashlib.sha256(
        json.dumps(envelope, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()
    return {
        **envelope,
        "integrity": {
            "algorithm": "sha256",
            "checksum": checksum,
        },
    }


def _source_version_identifier() -> str:
    commit = _source_commit()
    return commit if not _repo_is_dirty() else f"{commit}-dirty"


def _source_commit() -> str:
    result = subprocess.run(
        ["git", "-C", str(_repo_root()), "rev-parse", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def _repo_is_dirty() -> bool:
    result = subprocess.run(
        ["git", "-C", str(_repo_root()), "status", "--porcelain"],
        check=True,
        capture_output=True,
        text=True,
    )
    return bool(result.stdout.strip())


def _repo_root() -> Path:
    current = Path(__file__).resolve()
    for candidate in (current.parent, *current.parents):
        if (candidate / ".git").exists():
            return candidate
    raise RuntimeError("Could not locate the nested runescape-rl git repository root.")


def _python_root() -> Path:
    return Path(__file__).resolve().parents[2]


def _run_json_script(script_name: str, *args: str) -> dict[str, Any]:
    script = _python_root() / "scripts" / script_name
    with tempfile.TemporaryDirectory(prefix="pr1-fixture-") as temp_dir:
        output_path = Path(temp_dir) / "payload.json"
        result = subprocess.run(
            [sys.executable, str(script), *args, "--output", str(output_path)],
            cwd=str(_python_root()),
            check=False,
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            raise RuntimeError(
                f"Fixture generation script failed: {script_name}\n"
                f"stdout:\n{result.stdout}\n"
                f"stderr:\n{result.stderr}"
            )
        return json.loads(output_path.read_text(encoding="utf-8"))
