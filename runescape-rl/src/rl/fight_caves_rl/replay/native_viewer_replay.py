from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Mapping, Sequence

from fight_caves_rl.envs.action_mapping import NormalizedAction
from fight_caves_rl.replay.trace_packs import TracePack, resolve_trace_pack

NATIVE_VIEWER_REPLAY_SCHEMA_ID = "fc_native_viewer_replay_v1"
NATIVE_VIEWER_REPLAY_SCHEMA_VERSION = 1

DEFAULT_AMMO = 1000
DEFAULT_PRAYER_POTIONS = 8
DEFAULT_SHARKS = 20


def build_native_viewer_replay_from_trace_pack(trace_pack_id: str) -> dict[str, Any]:
    trace_pack = resolve_trace_pack(trace_pack_id)
    return {
        "schema_id": NATIVE_VIEWER_REPLAY_SCHEMA_ID,
        "schema_version": NATIVE_VIEWER_REPLAY_SCHEMA_VERSION,
        "source_kind": "trace_pack",
        "source_ref": str(trace_pack.identity.contract_id),
        "seed": int(trace_pack.default_seed),
        "start_wave": int(trace_pack.start_wave),
        "ammo": DEFAULT_AMMO,
        "prayer_potions": DEFAULT_PRAYER_POTIONS,
        "sharks": DEFAULT_SHARKS,
        "actions": [
            {
                "words": list(_pack_normalized_action(step.action)),
                "label": _label_for_normalized_action(step.action),
            }
            for step in trace_pack.steps
        ],
    }


def build_native_viewer_replay_from_replay_pack(
    replay_pack_path: str | Path,
    *,
    episode_index: int = 0,
) -> dict[str, Any]:
    path = Path(replay_pack_path)
    payload = json.loads(path.read_text(encoding="utf-8"))
    episodes = list(payload.get("episodes", ()))
    if episode_index < 0 or episode_index >= len(episodes):
        raise IndexError(
            f"Replay pack episode_index {episode_index} is out of range for {len(episodes)} episodes."
        )

    episode = dict(episodes[episode_index])
    semantic_state = dict(episode.get("semantic_episode_state", {}))
    episode_reset_summary = dict(episode.get("episode_reset_summary", {}))
    steps = list(episode.get("steps", ()))
    actions = []
    for step in steps:
        packed = _coerce_packed_action(step.get("action"))
        actions.append(
            {
                "words": list(packed),
                "label": _label_for_packed_action(packed),
            }
        )

    return {
        "schema_id": NATIVE_VIEWER_REPLAY_SCHEMA_ID,
        "schema_version": NATIVE_VIEWER_REPLAY_SCHEMA_VERSION,
        "source_kind": "replay_pack",
        "source_ref": f"{path.stem}_episode_{episode_index}",
        "seed": int(episode["seed"]),
        "start_wave": int(
            semantic_state.get(
                "wave",
                episode_reset_summary.get("wave", 1),
            )
        ),
        "ammo": int(episode_reset_summary.get("ammo", DEFAULT_AMMO)),
        "prayer_potions": int(
            episode_reset_summary.get(
                "prayer_potions",
                episode_reset_summary.get("prayer_potion_count", DEFAULT_PRAYER_POTIONS),
            )
        ),
        "sharks": int(episode_reset_summary.get("sharks", DEFAULT_SHARKS)),
        "actions": actions,
    }


def write_native_viewer_replay(path: str | Path, replay: Mapping[str, Any]) -> Path:
    output = Path(path)
    output.parent.mkdir(parents=True, exist_ok=True)
    actions = list(replay["actions"])
    lines = [
        f"schema_id {replay['schema_id']}",
        f"schema_version {int(replay['schema_version'])}",
        f"source_kind {replay['source_kind']}",
        f"source_ref {replay['source_ref']}",
        f"seed {int(replay['seed'])}",
        f"start_wave {int(replay['start_wave'])}",
        f"ammo {int(replay['ammo'])}",
        f"prayer_potions {int(replay['prayer_potions'])}",
        f"sharks {int(replay['sharks'])}",
        f"action_count {len(actions)}",
    ]
    for action in actions:
        words = tuple(int(value) for value in action["words"])
        if len(words) != 4:
            raise ValueError(f"Expected 4 packed action words, got {words!r}")
        lines.append(
            f"action {words[0]} {words[1]} {words[2]} {words[3]} {str(action['label'])}"
        )
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return output


def _pack_normalized_action(action: NormalizedAction) -> tuple[int, int, int, int]:
    words = [0, 0, 0, 0]
    words[0] = int(action.action_id)
    if action.action_id == 1:
        if action.tile is None:
            raise ValueError("walk_to_tile replay actions require tile coordinates.")
        words[1] = int(action.tile.x)
        words[2] = int(action.tile.y)
        words[3] = int(action.tile.level)
    elif action.action_id == 2:
        if action.visible_npc_index is None:
            raise ValueError("attack_visible_npc replay actions require visible_npc_index.")
        words[1] = int(action.visible_npc_index)
    elif action.action_id == 3:
        prayer_to_index = {
            "protect_from_magic": 0,
            "protect_from_missiles": 1,
            "protect_from_melee": 2,
        }
        if action.prayer not in prayer_to_index:
            raise ValueError(f"Unsupported protection prayer for replay export: {action.prayer!r}.")
        words[1] = int(prayer_to_index[str(action.prayer)])
    return tuple(words)


def _label_for_normalized_action(action: NormalizedAction) -> str:
    if action.action_id == 3 and action.prayer is not None:
        prayer_name = str(action.prayer).replace("protect_from_", "")
        return f"toggle_prayer_{prayer_name}"
    return _label_for_packed_action(_pack_normalized_action(action))


def _label_for_packed_action(words: Sequence[int]) -> str:
    action_id = int(words[0])
    if action_id == 0:
        return "wait"
    if action_id == 1:
        return "walk_to_tile"
    if action_id == 2:
        return "attack_visible_npc"
    if action_id == 3:
        prayer_labels = {
            0: "toggle_prayer_magic",
            1: "toggle_prayer_missiles",
            2: "toggle_prayer_melee",
        }
        return prayer_labels.get(int(words[1]), "toggle_prayer_unknown")
    if action_id == 4:
        return "eat_shark"
    if action_id == 5:
        return "drink_prayer_potion"
    if action_id == 6:
        return "toggle_run"
    return f"action_{action_id}"


def _coerce_packed_action(action_payload: object) -> tuple[int, int, int, int]:
    if not isinstance(action_payload, Sequence) or isinstance(action_payload, (str, bytes)):
        raise TypeError(f"Replay pack action must be a sequence of ints, got {type(action_payload)!r}.")
    values = [int(value) for value in action_payload]
    while len(values) < 4:
        values.append(0)
    return tuple(values[:4])
