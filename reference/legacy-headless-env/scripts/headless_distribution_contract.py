#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import posixpath
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path


REQUIRED_ARCHIVE_MEMBERS = (
    "fight-caves-headless.jar",
    "headless_data_allowlist.toml",
    "headless_manifest.toml",
    "headless_scripts.txt",
    "game.properties",
    "run-headless.sh",
    "run-headless.bat",
    "data/minigame/tzhaar_fight_cave/tzhaar_fight_cave_waves.toml",
)


def _sanitize_version(value: str) -> str:
    sanitized = "".join(
        character if character.isalnum() or character in "._-" else "-"
        for character in value
    ).strip(".-")
    return sanitized or "dev"


def _remove_existing_artifacts(repo_root: Path) -> None:
    for candidate in repo_root.glob("**/fight-caves-headless*.zip"):
        if candidate.is_file():
            candidate.unlink()
    distributions_root = repo_root / "game" / "build" / "distributions"
    for candidate in distributions_root.glob("fight-caves-headless*"):
        if candidate.is_dir():
            shutil.rmtree(candidate)
    install_root = repo_root / "game" / "build" / "install" / "fight-caves-headless"
    if install_root.exists():
        shutil.rmtree(install_root)


def _run_gradle_build(repo_root: Path) -> None:
    result = subprocess.run(
        ["./gradlew", "--no-daemon", ":game:headlessDistZip", ":game:installHeadlessDist"],
        cwd=str(repo_root),
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(
            "Headless distribution build failed.\n"
            f"cwd: {repo_root}\n"
            f"stdout:\n{result.stdout}\n"
            f"stderr:\n{result.stderr}"
        )


def _write_archive_from_install_root(install_root: Path, zip_path: Path) -> None:
    zip_path.parent.mkdir(parents=True, exist_ok=True)
    prefix = zip_path.stem
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(install_root.rglob("*")):
            relative_path = path.relative_to(install_root)
            archive_name = posixpath.join(prefix, relative_path.as_posix())
            if path.is_dir():
                archive.writestr(archive_name.rstrip("/") + "/", "")
            else:
                archive.write(path, arcname=archive_name)


def _validate_archive_contract(zip_path: Path) -> dict[str, object]:
    if zip_path.parent.name != "distributions":
        raise FileNotFoundError(
            f"Headless distribution must live under game/build/distributions: {zip_path}"
        )

    with zipfile.ZipFile(zip_path) as archive:
        members = tuple(archive.namelist())

    prefixes = {
        member.split("/", 1)[0]
        for member in members
        if member and not member.endswith("/")
    }
    if prefixes != {zip_path.stem}:
        raise FileNotFoundError(
            "Headless distribution must contain exactly one top-level directory "
            f"matching the archive stem. archive={zip_path} prefixes={sorted(prefixes)}"
        )

    missing = [
        member
        for member in REQUIRED_ARCHIVE_MEMBERS
        if f"{zip_path.stem}/{member}" not in members
    ]
    if missing:
        raise FileNotFoundError(
            f"Headless distribution is missing required members {missing}: {zip_path}"
        )

    return {
        "archive_path": str(zip_path),
        "archive_name": zip_path.name,
        "archive_root": zip_path.stem,
        "member_count": len(members),
        "required_members_checked": list(REQUIRED_ARCHIVE_MEMBERS),
    }


def resolve_headless_distribution(
    *,
    repo_root: Path,
    game_version: str,
) -> dict[str, object]:
    repo_root = repo_root.resolve()
    expected_root = repo_root / "game" / "build" / "distributions"
    expected_root.mkdir(parents=True, exist_ok=True)
    install_root = repo_root / "game" / "build" / "install" / "fight-caves-headless"
    safe_game_version = _sanitize_version(game_version)
    expected_path = expected_root / f"fight-caves-headless-{safe_game_version}.zip"

    matches = sorted(
        (
            candidate
            for candidate in repo_root.glob("**/fight-caves-headless*.zip")
            if candidate.is_file()
        ),
        key=lambda candidate: candidate.stat().st_mtime,
        reverse=True,
    )
    expected_matches = sorted(
        (
            candidate
            for candidate in expected_root.glob("fight-caves-headless*.zip")
            if candidate.is_file()
        ),
        key=lambda candidate: candidate.stat().st_mtime,
        reverse=True,
    )

    strategy = "existing_canonical_zip"
    if expected_matches:
        selected = expected_matches[0]
    elif matches:
        selected = matches[0]
        normalized = expected_root / selected.name
        if selected.resolve() != normalized.resolve():
            shutil.copy2(selected, normalized)
        selected = normalized
        strategy = "normalized_existing_zip"
    elif install_root.is_dir():
        _write_archive_from_install_root(install_root, expected_path)
        selected = expected_path
        strategy = "synthesized_from_install_tree"
    else:
        raise FileNotFoundError(
            "No fight-caves-headless*.zip was produced anywhere under the repo root "
            "and the install-tree fallback was absent.\n"
            f"repo_root={repo_root}\n"
            f"install_root={install_root}"
        )

    contract = _validate_archive_contract(selected)
    contract.update(
        {
            "strategy": strategy,
            "safe_game_version": safe_game_version,
        }
    )
    return contract


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Build, resolve, and validate the headless distribution contract expected by "
            "the RL bridge/benchmark consumers."
        )
    )
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path.cwd(),
        help="fight-caves-RL repo root (defaults to current working directory)",
    )
    parser.add_argument(
        "--game-version",
        type=str,
        default=os.environ.get("GAME_VERSION")
        or os.environ.get("GITHUB_REF_NAME")
        or "dev",
        help="raw version/ref value used to derive the canonical archive name",
    )
    parser.add_argument(
        "--build",
        action="store_true",
        help="remove existing headless zips/install tree and rebuild before validation",
    )
    args = parser.parse_args()

    if args.build:
        _remove_existing_artifacts(args.repo_root)
        _run_gradle_build(args.repo_root)

    contract = resolve_headless_distribution(
        repo_root=args.repo_root,
        game_version=args.game_version,
    )
    print(json.dumps(contract, indent=2, sort_keys=True))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # pragma: no cover - CLI error path
        print(str(exc), file=sys.stderr)
        raise
