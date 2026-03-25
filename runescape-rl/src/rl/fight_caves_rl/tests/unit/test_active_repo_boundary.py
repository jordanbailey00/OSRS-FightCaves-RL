from fight_caves_rl.utils.paths import source_root


def test_active_runescape_rl_tree_contains_no_java_or_kotlin_sources():
    active_root = source_root()
    disallowed = sorted(
        str(path.relative_to(active_root))
        for pattern in ("*.kt", "*.java")
        for path in active_root.rglob(pattern)
    )

    assert disallowed == []


def test_active_runescape_rl_tree_contains_no_legacy_jvm_roots():
    active_root = source_root()

    for relative in ("src/headless-env", "src/headed-env", "src/rsps", "src/native-env"):
        assert not (active_root / relative).exists(), relative


def test_active_runescape_rl_tree_uses_component_split_roots():
    active_root = source_root()

    for relative in ("src/training-env", "src/demo-env", "src/rl"):
        assert (active_root / relative).is_dir(), relative
