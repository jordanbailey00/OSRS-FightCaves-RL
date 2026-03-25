package sim.engine.map.collision

import org.rsmod.game.pathfinder.collision.CollisionStrategies
import org.rsmod.game.pathfinder.collision.CollisionStrategy
import sim.cache.definition.data.NPCDefinition
import sim.engine.entity.character.Character
import sim.engine.entity.character.npc.NPC

object CollisionStrategyProvider {
    fun get(character: Character): CollisionStrategy = when (character) {
        is NPC -> get(character.def)
        else -> CollisionStrategies.Normal
    }

    fun get(def: NPCDefinition) = when (def["collision", ""]) {
        "sea" -> CollisionStrategies.Blocked
        "indoors" -> CollisionStrategies.Indoors
        "outdoors" -> CollisionStrategies.Outdoors
        "sky" -> CollisionStrategies.LineOfSight
        else -> CollisionStrategies.Normal
    }
}
