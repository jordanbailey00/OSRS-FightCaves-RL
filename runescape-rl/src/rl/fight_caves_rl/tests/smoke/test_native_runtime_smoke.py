from __future__ import annotations

from pathlib import Path

from fight_caves_rl.native_runtime import (
    ENV_BACKEND_NATIVE_PREVIEW,
    NativeRuntimeBuildConfig,
    build_native_runtime,
    expected_descriptor_bundle,
    load_native_runtime,
    load_native_runtime_from_config,
    native_library_filename,
)


def test_native_runtime_build_load_query_and_close_smoke(tmp_path: Path):
    build_dir = tmp_path / "native-build"
    library_path = build_native_runtime(
        NativeRuntimeBuildConfig(output_dir=build_dir, force_rebuild=True)
    )

    assert library_path.name == native_library_filename()
    assert library_path.is_file()

    with load_native_runtime(library_path=library_path) as runtime:
        assert runtime.closed is False
        assert runtime.descriptor_count == 5
        assert runtime.runtime_version == "pr5a_core_combat_terminal_v1"
        assert runtime.descriptor_bundle() == expected_descriptor_bundle()

    assert runtime.closed is True


def test_native_runtime_can_be_loaded_from_explicit_native_backend_config(tmp_path: Path):
    config = {
        "env": {
            "env_backend": ENV_BACKEND_NATIVE_PREVIEW,
        }
    }

    with load_native_runtime_from_config(
        config,
        build_config=NativeRuntimeBuildConfig(output_dir=tmp_path, force_rebuild=True),
    ) as runtime:
        assert runtime.descriptor_count == 5
        assert runtime.descriptor_bundle()["summary"]["action_count"] == 7
