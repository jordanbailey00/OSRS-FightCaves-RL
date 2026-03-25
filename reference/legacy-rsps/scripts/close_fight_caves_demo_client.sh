#!/usr/bin/env bash
set -euo pipefail

POWERSHELL_EXE="${POWERSHELL_EXE:-powershell.exe}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"$POWERSHELL_EXE" \
    -NoProfile \
    -ExecutionPolicy Bypass \
    -File "$(wslpath -w "$SCRIPT_DIR/close_fight_caves_demo_client.ps1")"
