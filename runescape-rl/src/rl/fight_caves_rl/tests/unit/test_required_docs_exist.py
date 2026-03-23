from fight_caves_rl.utils.paths import source_root


def test_required_root_docs_exist_and_contain_rewrite_anchor_terms():
    root = source_root().parent
    expected = {
        root / "README.md": (
            "C/C++ rewrite",
            "runescape-rl/src/headless-env",
            "docs/plan.md",
        ),
        root / "docs/plan.md": (
            "FastFightCavesKernelRuntime",
            "HeadlessObservationBuilder.kt",
            "fight_caves_v2_reward_features_v1",
        ),
        root / "docs/pr_plan.md": (
            "PR1: Contract Freeze And Golden Fixtures",
            "PR7: Python `v2_fast` Integration Cutover",
            "PR8: Performance Validation And Default Cutover",
        ),
    }

    for path, snippets in expected.items():
        assert path.is_file(), path
        text = path.read_text(encoding="utf-8")
        for snippet in snippets:
            assert snippet in text, f"{snippet!r} missing from {path}"
