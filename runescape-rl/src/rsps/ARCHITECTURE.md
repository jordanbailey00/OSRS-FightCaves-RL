# rsps ARCHITECTURE

## Internal structure (high level)

- `game/`: gameplay content and demo-profile hooks.
- `engine/`: runtime engine/state/event systems.
- `network/`: login/game/file-server protocol surfaces.
- `cache/`, `config/`, `types/`, `buffer/`: shared data and protocol support.
- `void-client/`: external client source checkout used for stock-client understanding/build paths.

## Headed demo runtime flow

1. Demo profile server starts (`:game:runFightCavesDemo`).
2. Stock client connects (machine-specific WSL-IP path on current host).
3. Post-login path lands user into constrained Fight Caves runtime.
4. Starter-state and reset paths enforce canonical episode-start behavior.
5. Headed observability records session and validation artifacts.

## Control boundaries

- Server/runtime is authoritative for mechanics and starter state.
- Client is authoritative for UI/render/assets/gameframe presentation.
- Backend-control adapter injects actions without UI clicking for replay/live inference slices.

## Relationship to sibling modules

- Consumes checkpoints and control from `training-env/rl` for headed demos.
- Is the active headed runtime counterpart to `training-env` headless path.
- Supersedes `demo-env` as default headed target.
