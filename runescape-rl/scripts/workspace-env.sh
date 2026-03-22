#!/usr/bin/env bash

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
workspace_root="$(cd "$script_dir/.." && pwd)"
runtime_root="${FIGHT_CAVES_RUNTIME_ROOT:-$(cd "$workspace_root/.." && pwd)/runescape-rl-runtime}"
export WORKSPACE_ROOT="$workspace_root"
export FIGHT_CAVES_RUNTIME_ROOT="$runtime_root"

export JAVA_HOME="$runtime_root/tool-state/workspace-tools/jdk-21"
export GRADLE_USER_HOME="$runtime_root/cache/gradle"
export PIP_CACHE_DIR="$runtime_root/cache/pip"
export UV_CACHE_DIR="$runtime_root/cache/uv"
export UV_PYTHON_INSTALL_DIR="$runtime_root/cache/python"
export XDG_CACHE_HOME="$runtime_root/cache/general"
export XDG_CONFIG_HOME="$runtime_root/tool-state/agent/config"
export TMPDIR="$runtime_root/tmp/run"

mkdir -p \
  "$GRADLE_USER_HOME" \
  "$PIP_CACHE_DIR" \
  "$UV_CACHE_DIR" \
  "$UV_PYTHON_INSTALL_DIR" \
  "$XDG_CACHE_HOME" \
  "$XDG_CONFIG_HOME" \
  "$TMPDIR" \
  "$runtime_root/artifacts" \
  "$runtime_root/logs" \
  "$runtime_root/reports"

export PATH="$JAVA_HOME/bin:$PATH"
