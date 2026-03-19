# Headless Script Loading (Step 4)

## Purpose

Headless mode must load only the Fight Caves script closure while headed/oracle mode continues loading the full `scripts.txt` list.

## Artifacts

- Allowlist file: `config/headless_scripts.txt`
- Manifest source of truth: `config/headless_manifest.toml` -> `[scripts].required_classes`

## Runtime Behavior

1. `HeadlessMain.bootstrap` loads `config/headless_scripts.txt`.
2. `RuntimeBootstrap.preloadRuntime` passes this allowlist to `ContentLoader` only in headless mode.
3. Headed/oracle runtime behavior is unchanged and still loads the full generated `scripts.txt` list.

## Startup Validation

When `loadContentScripts=true` in headless mode, startup now fails fast if any of the following checks fail:

1. A script declared in `config/headless_scripts.txt` is missing from generated `scripts.txt`.
2. Any allowlisted script did not load.
3. Any script loaded outside the allowlist.
4. Drift guard mismatch between `config/headless_scripts.txt` and `config/headless_manifest.toml` `[scripts].required_classes`.
5. Required Fight Caves hook registrations are missing:
   - Cave entry/exit object operations.
   - Jad magic/range attack handlers.
   - Tz-Kek damage/death/despawn progression hooks.
   - Jad constitution threshold hook for healer spawn.
   - Healer condition/moved/timer hooks.

## Settings

Optional overrides:

- `headless.scripts.allowlist.path` (default `config/headless_scripts.txt`)
- `headless.manifest.path` (default `config/headless_manifest.toml`)

## Step 4 Tests

- `HeadlessScriptRegistryContainsFightCaveHandlersTest`
- `HeadlessScriptRegistryExcludesUnrelatedSystemsTest`
- `HeadlessSingleWaveScriptSmokeTest`
