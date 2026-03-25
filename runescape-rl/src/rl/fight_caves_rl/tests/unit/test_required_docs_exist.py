from fight_caves_rl.utils.paths import source_root


def test_required_root_docs_exist_and_contain_rewrite_anchor_terms():
    root = source_root().parent
    expected = {
        root / "README.md": (
            "C/C++ rewrite",
            "docs/plan.md",
            "docs/pr_plan.md",
        ),
        root / "docs/plan.md": (
            "reference/legacy-headless-env",
            "reference/legacy-rsps",
            "fight_caves_v2_reward_features_v1",
        ),
        root / "docs/pr_plan.md": (
            "PR8a.5: Rollout Boundary And Tiny-Batch Throughput Reduction",
            "DV3: Agent Attach, Replay, And Inspector Overlays",
            "Repo Boundary Note",
            "Performance-Track Pause",
            "Viewer-Track Priority",
        ),
    }

    for path, snippets in expected.items():
        assert path.is_file(), path
        text = path.read_text(encoding="utf-8")
        for snippet in snippets:
            assert snippet in text, f"{snippet!r} missing from {path}"
