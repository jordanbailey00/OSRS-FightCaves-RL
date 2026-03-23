from __future__ import annotations

ENV_BACKEND_NATIVE_PREVIEW = "native_preview"
NATIVE_RUNTIME_ABI_VERSION = 1
NATIVE_RUNTIME_ID = "fight_caves_native_runtime_pr2"
NATIVE_RUNTIME_VERSION = 1
NATIVE_RUNTIME_MODE = "core_combat_terminal_scaffold"
NATIVE_RUNTIME_VECENV_GUARD_MESSAGE = (
    "env_backend='native_preview' currently exposes only the native direct-test scaffold "
    "through PR5a. Standard vecenv/train usage remains out of scope until PR7."
)

NATIVE_CORE_TRACE_IDS = {
    "single_wave": 1,
    "full_run": 2,
}
