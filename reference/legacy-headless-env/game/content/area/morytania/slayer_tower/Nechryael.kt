package content.area.morytania.slayer_tower

import sim.engine.Script
import sim.engine.client.instruction.handle.interactPlayer
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.Players
import sim.engine.entity.character.player.name
import sim.engine.entity.character.sound
import sim.engine.map.collision.random
import sim.engine.queue.softQueue
import sim.engine.timer.toTicks
import sim.types.random
import java.util.concurrent.TimeUnit

class Nechryael : Script {

    init {
        npcCombatAttack("nechryael") { (target) ->
            if (target !is Player) {
                return@npcCombatAttack
            }
            val spawns = target["death_spawns", 0]
            if (spawns >= 2) {
                return@npcCombatAttack
            }
            if (random.nextInt(5) == 0) { // Unknown rate
                val tile = tile.toCuboid(1).random(this) ?: return@npcCombatAttack
                // TODO gfx
                val spawn = NPCs.add("death_spawn", tile)
                val name = target.name
                spawn.softQueue("despawn", TimeUnit.SECONDS.toTicks(60)) {
                    NPCs.remove(spawn)
                    Players.find(name)?.dec("death_spawns")
                }
                spawn.anim("death_spawn")
                spawn.interactPlayer(target, "Attack")
                target.sound("death_spawn")
                target.inc("death_spawns")
            }
        }
    }
}
