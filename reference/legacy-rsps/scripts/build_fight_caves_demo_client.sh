#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POWERSHELL_EXE="${POWERSHELL_EXE:-powershell.exe}"
SCRIPT_PATH="$ROOT_DIR/scripts/build_fight_caves_demo_client.ps1"
OUTPUT_JAR="${1:-$ROOT_DIR/../.artifacts/void-client/void-client-fight-caves-demo.jar}"

"$POWERSHELL_EXE" \
    -NoProfile \
    -ExecutionPolicy Bypass \
    -File "$(wslpath -w "$SCRIPT_PATH")" \
    -SourceDir "$(wslpath -w "$ROOT_DIR/void-client/client/src")" \
    -ResourceDir "$(wslpath -w "$ROOT_DIR/void-client/client/resources")" \
    -LibJar "$(wslpath -w "$ROOT_DIR/void-client/libs/clientlibs.jar")" \
    -OutputJar "$(wslpath -w "$OUTPUT_JAR")"
