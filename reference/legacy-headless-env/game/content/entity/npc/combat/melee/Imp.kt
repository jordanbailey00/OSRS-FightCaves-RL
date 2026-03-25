package content.entity.npc.combat.melee

import content.entity.gfx.areaGfx
import sim.engine.Script
import sim.engine.entity.character.areaSound
import sim.engine.entity.character.mode.PauseMode
import sim.engine.entity.character.mode.Retreat
import sim.engine.entity.character.move.tele
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.skill.Skill
import sim.engine.map.collision.random
import sim.engine.queue.softQueue
import sim.types.Tile
import sim.types.random

class Imp : Script {

    init {
        npcSpawn("imp") {
            softTimers.start("teleport_timer")
        }

        npcTimerStart("teleport_timer") { random.nextInt(50, 200) }

        npcTimerTick("teleport_timer") {
            teleportImp(this, teleportChance)
            // https://x.com/JagexAsh/status/1711280844504007132
            random.nextInt(50, 200)
        }

        npcCombatDamage("imp") { (source, _, damage) ->
            if (levels.get(Skill.Constitution) - damage > 0 && random.nextDouble() < retreatChance) {
                if (levels.get(Skill.Constitution) - damage < 10) {
                    softQueue("imp_retreat") {
                        mode = Retreat(npc, source)
                    }
                } else if (mode !is Retreat) {
                    teleportImp(this, teleportChanceHit)
                }
            }
        }
    }

    private val teleportRadiusMax = 20
    private val teleportRadiusMin = 5
    private val teleportChance = 0.25
    private val teleportChanceHit = 0.10
    private val telePoofVfxRadius = 5
    private val retreatChance = 0.50

    fun randomValidTile(npc: NPC): Tile {
        repeat(10) {
            val dest = npc.tile.toCuboid(teleportRadiusMax).random(npc) ?: return@repeat
            if (dest.region == npc.tile.region && dest != npc.tile && npc.tile.distanceTo(dest) >= teleportRadiusMin) {
                return dest
            }
        }
        return npc.tile
    }

    fun teleportImp(npc: NPC, chance: Double) {
        if (npc.queue.contains("death")) {
            return
        }
        if (random.nextDouble() > chance) {
            return
        }
        npc.softTimers.restart("teleport_timer")
        val destination = randomValidTile(npc)
        if (destination == npc.tile) {
            return
        }

        npc.softQueue("imp_teleport") {
            areaSound("smoke_puff", npc.tile, telePoofVfxRadius)
            areaGfx("imp_puff", npc.tile)
            npc.steps.clear()
            val mode = npc.mode
            npc.mode = PauseMode
            npc.tele(destination)
            npc.delay(1)
            npc.gfx("imp_puff")
            npc.mode = mode
        }
    }
}
