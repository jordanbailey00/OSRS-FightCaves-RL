#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POWERSHELL_EXE="${POWERSHELL_EXE:-powershell.exe}"
PATCHED_JAR="$ROOT_DIR/../.artifacts/void-client/void-client-fight-caves-demo.jar"
STOCK_JAR="$ROOT_DIR/../.artifacts/void-client/void-client-1.0.1.jar"
LOG_DIR="$ROOT_DIR/data/fight_caves_demo/client_logs"

ADDRESS=""
PORT=43594
JAVA_EXE="${FIGHT_CAVES_DEMO_JAVA_EXE:-}"
JAR_PATH="$PATCHED_JAR"
USERNAME=""
PASSWORD=""
LOGIN_DELAY_SECONDS=4
DRY_RUN=false
USE_STOCK_JAR=false
SKIP_BUILD=false
SKIP_CACHE_SYNC=false
SHOW_BOOTSTRAP=false
DEFAULT_DEMO_LOGIN=false

usage() {
    cat <<'EOF'
Usage:
  ./scripts/run_fight_caves_demo_client.sh [options]

Options:
  --address <ip>           Override the WSL IP used by the stock client.
  --port <port>            Override the server port. Default: 43594.
  --java-exe <path>        Override the Windows Java executable path.
  --jar <path>             Override the client jar path.
  --stock-jar              Force the stock release jar instead of the demo convenience jar.
  --skip-build             Do not auto-build the demo convenience jar if it is missing.
  --skip-cache-sync        Skip syncing RSPS cache files into the Windows client cache.
  --show-bootstrap         Show the real client window during bootstrap instead of the demo loading window.
  --hide-bootstrap         Keep the real client hidden until it reaches the in-game states. This is the default.
  --username <name>        Optional login username.
  --password <value>       Optional login autofill password.
  --login-delay <seconds>  Delay before autofill sends login keys. Default: 4.
  --dry-run                Print the resolved launch command without starting the client.
  --help                   Show this help text.

Notes:
  - The default runtime artifact is a lightly modified demo convenience jar built from void-client source.
  - Use --stock-jar to force the unmodified release jar fallback.
  - On this machine the stock client should target the WSL IP, not 127.0.0.1.
  - The convenience jar defaults to a stable disposable demo account if no credentials are provided.
  - The launcher syncs RSPS cache files into the Windows client cache before launch unless --skip-cache-sync is used.
EOF
}

to_windows_path() {
    local path="$1"
    if [[ "$path" =~ ^[A-Za-z]:\\ ]] || [[ "$path" =~ ^\\\\ ]]; then
        printf '%s' "$path"
        return
    fi
    wslpath -w "$path"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --address)
            ADDRESS="${2:-}"
            shift 2
            ;;
        --port)
            PORT="${2:-}"
            shift 2
            ;;
        --java-exe)
            JAVA_EXE="${2:-}"
            shift 2
            ;;
        --jar)
            JAR_PATH="${2:-}"
            shift 2
            ;;
        --stock-jar)
            USE_STOCK_JAR=true
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --skip-cache-sync)
            SKIP_CACHE_SYNC=true
            shift
            ;;
        --show-bootstrap)
            SHOW_BOOTSTRAP=true
            shift
            ;;
        --hide-bootstrap)
            SHOW_BOOTSTRAP=false
            shift
            ;;
        --username)
            USERNAME="${2:-}"
            shift 2
            ;;
        --password)
            PASSWORD="${2:-}"
            shift 2
            ;;
        --login-delay)
            LOGIN_DELAY_SECONDS="${2:-}"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

if $USE_STOCK_JAR; then
    JAR_PATH="$STOCK_JAR"
fi

if [[ -z "$ADDRESS" ]]; then
    ADDRESS="$(hostname -I | awk '{print $1}')"
fi

if [[ -z "$ADDRESS" ]]; then
    echo "Unable to resolve the current WSL IP. Pass --address explicitly." >&2
    exit 1
fi

if [[ "$JAR_PATH" == "$PATCHED_JAR" && ! -f "$JAR_PATH" && "$SKIP_BUILD" == false ]]; then
    "$ROOT_DIR/scripts/build_fight_caves_demo_client.sh" "$PATCHED_JAR"
fi

if [[ ! -f "$JAR_PATH" ]]; then
    echo "Client jar not found at: $JAR_PATH" >&2
    exit 1
fi

if [[ -n "$USERNAME" && -z "$PASSWORD" ]] || [[ -z "$USERNAME" && -n "$PASSWORD" ]]; then
    echo "Both --username and --password must be provided together." >&2
    exit 1
fi

if [[ "$JAR_PATH" == "$PATCHED_JAR" && -z "$USERNAME" && -z "$PASSWORD" ]]; then
    USERNAME="fcdemo01"
    PASSWORD="pass123"
    DEFAULT_DEMO_LOGIN=true
fi

mkdir -p "$LOG_DIR"
timestamp="$(date +%Y%m%dT%H%M%S)"
stdout_path="$LOG_DIR/client-${timestamp}-stdout.log"
stderr_path="$LOG_DIR/client-${timestamp}-stderr.log"
launcher_ps1="$ROOT_DIR/scripts/launch_fight_caves_demo_client.ps1"

cmd=(
    "$POWERSHELL_EXE"
    -NoProfile
    -ExecutionPolicy
    Bypass
    -File
    "$(to_windows_path "$launcher_ps1")"
    -JarPath
    "$(to_windows_path "$JAR_PATH")"
    -Address
    "$ADDRESS"
    -Port
    "$PORT"
    -StdoutPath
    "$(to_windows_path "$stdout_path")"
    -StderrPath
    "$(to_windows_path "$stderr_path")"
)

if [[ "$SKIP_CACHE_SYNC" == false ]]; then
    cmd+=(
        -CacheSourceDir
        "$(to_windows_path "$ROOT_DIR/data/cache")"
    )
fi

if [[ -n "$JAVA_EXE" ]]; then
    cmd+=(-JavaExe "$(to_windows_path "$JAVA_EXE")")
fi

if [[ -n "$USERNAME" ]]; then
    cmd+=(
        -Username
        "$USERNAME"
        -Password
        "$PASSWORD"
        -LoginDelaySeconds
        "$LOGIN_DELAY_SECONDS"
    )
fi

if [[ "$SHOW_BOOTSTRAP" == true ]]; then
    cmd+=(-ShowBootstrap)
fi

if $DRY_RUN; then
    printf 'Resolved address: %s\n' "$ADDRESS"
    if [[ -n "$USERNAME" ]]; then
        printf 'Login username: %s%s\n' "$USERNAME" "$( [[ "$DEFAULT_DEMO_LOGIN" == true ]] && printf ' (default demo account)' )"
    fi
    printf 'Stdout log: %s\n' "$stdout_path"
    printf 'Stderr log: %s\n' "$stderr_path"
    printf 'Launch command:\n'
    printf '  %q' "${cmd[@]}"
    printf '\n'
    exit 0
fi

if [[ "$DEFAULT_DEMO_LOGIN" == true ]]; then
    printf 'Using default demo account: %s\n' "$USERNAME"
fi

"${cmd[@]}"
