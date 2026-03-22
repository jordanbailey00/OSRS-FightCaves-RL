import content.area.karamja.tzhaar_city.JAD_HIT_CLIENT_DELAY
import content.area.karamja.tzhaar_city.JAD_HIT_TARGET_QUEUE_TICKS
import content.area.karamja.tzhaar_city.jadAttackSequence
import content.area.karamja.tzhaar_city.beginJadTelegraphForAttack
import content.area.karamja.tzhaar_city.recordJadAttackOutcome
import content.entity.combat.hit.hit
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.skill.Skill
import sim.engine.queue.strongQueue

internal fun spawnJadWithTelegraphedAttack(
    player: Player,
    attackId: String,
    baseDamage: Int = 100,
): NPC {
    val jad = NPCs.add("tztok_jad", player.tile.add(1, 0))
    NPCs.run()
    check(jad.beginJadTelegraphForAttack(attackId)) { "Failed to start Jad telegraph for attack '$attackId'." }
    jad.strongQueue("jad_test_hit_target_${jad.jadAttackSequence}", JAD_HIT_TARGET_QUEUE_TICKS) {
        val resolvedDamage =
            jad.hit(
            target = player,
            offensiveType = attackId,
            delay = JAD_HIT_CLIENT_DELAY,
            damage = baseDamage,
        )
        jad.recordJadAttackOutcome(player, resolvedDamage)
    }
    return jad
}

internal fun playerHitpoints(player: Player): Int = player.levels.get(Skill.Constitution)
