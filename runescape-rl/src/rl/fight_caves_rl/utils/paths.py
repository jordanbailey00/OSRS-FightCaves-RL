from os import environ
from pathlib import Path

def repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def workspace_root() -> Path:
    # Returns src/ (parent of the rl module root).
    return repo_root().parent


def source_root() -> Path:
    return workspace_root().parent


def default_runtime_root() -> Path:
    return source_root().parent / "runescape-rl-runtime"


def runtime_root() -> Path:
    configured = environ.get("FIGHT_CAVES_RUNTIME_ROOT")
    if configured:
        return Path(configured).expanduser().resolve()
    return default_runtime_root().resolve()


def runtime_subdir(*parts: str) -> Path:
    return runtime_root().joinpath(*parts)
