import content.entity.combat.hit.directHit
import content.quest.instance
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.skill.Skill

internal fun parityTestOverrides(): Map<String, String> =
    headlessTestOverrides() +
        mapOf(
            "runtime.mode" to RuntimeMode.Headed.id,
            "headless.map.regions" to "",
            "storage.disabled" to "true",
            "world.npcs.randomWalk" to "false",
            "events.shootingStars.enabled" to "false",
            "events.penguinHideAndSeek.enabled" to "false",
            "storage.autoSave.minutes" to "0",
            "bots.count" to "0",
            "bots.numberedNames" to "true",
            "bots.names" to "../data/bot_names.txt",
            "spawns.npcs" to "tzhaar_city.npc-spawns.toml",
            "spawns.items" to "tzhaar_city.items.toml",
            "spawns.objects" to "tzhaar_city.objs.toml",
        )

internal fun parityPlayerSetup(player: Player) {
    player["auto_retaliate"] = true
    player["god_mode"] = true
}

internal fun paritySingleWaveTrace(): List<HeadlessReplayStep> =
    listOf(
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 2),
        HeadlessReplayStep(HeadlessAction.AttackVisibleNpc(0), ticksAfter = 8),
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 10),
        HeadlessReplayStep(HeadlessAction.AttackVisibleNpc(0), ticksAfter = 8),
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 12),
    )

internal fun parityFullRunTrace(steps: Int = 160): List<HeadlessReplayStep> =
    List(steps) {
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 7)
    }

internal fun parityJadHealerTrace(): List<HeadlessReplayStep> =
    listOf(
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 5),
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 3),
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 4),
    )

internal fun parityTzKekSplitTrace(): List<HeadlessReplayStep> =
    listOf(
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 5),
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 7),
        HeadlessReplayStep(HeadlessAction.Wait, ticksAfter = 2),
    )

internal val fullRunWaveClearHook =
    ParityStepHook { _, stepIndex, _, player ->
        if (stepIndex < 0) {
            return@ParityStepHook
        }
        val npcs = fightCaveNpcs(player)
        for (npc in npcs) {
            npc.directHit(player, npc.levels.get(Skill.Constitution))
        }
    }

internal val jadHalfHpHook =
    ParityStepHook { _, stepIndex, _, player ->
        if (stepIndex != 0) {
            return@ParityStepHook
        }
        val jad = fightCaveNpcs(player).firstOrNull { it.id == "tztok_jad" } ?: return@ParityStepHook
        val damage = 1 + (jad.levels.getMax(Skill.Constitution) / 2)
        jad.directHit(player, damage)
    }

internal val tzKekSplitHook =
    ParityStepHook { _, stepIndex, _, player ->
        if (stepIndex < 0 || player["parity_tzkek_split_applied", false]) {
            return@ParityStepHook
        }

        val targets =
            fightCaveNpcs(player)
                .filter { it.id == "tz_kek" || it.id == "tz_kek_spawn_point" }
        if (targets.isEmpty()) {
            return@ParityStepHook
        }

        for (target in targets) {
            target.directHit(player, target.levels.get(Skill.Constitution))
        }
        player["parity_tzkek_split_applied"] = true
    }

private fun fightCaveNpcs(player: Player): List<NPC> {
    val instance = player.instance()
    return if (instance != null) {
        buildList {
            for (level in 0..3) {
                addAll(NPCs.at(instance.toLevel(level)))
            }
        }
    } else {
        NPCs.at(player.tile.regionLevel).toList()
    }
}







