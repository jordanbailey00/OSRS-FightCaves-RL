package content.entity.effect

import sim.engine.data.definition.NPCDefinitions
import sim.engine.entity.character.Character
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.npc.flagTransform
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.appearance
import sim.engine.entity.character.player.flagAppearance
import sim.engine.map.collision.CollisionStrategyProvider

fun Character.clearTransform() {
    if (this is Player) {
        appearance.apply {
            emote = 1426
            transform = -1
            size = 1
            idleSound = -1
            crawlSound = -1
            walkSound = -1
            runSound = -1
            soundDistance = 0
        }
        flagAppearance()
    } else if (this is NPC) {
        visuals.transform.id = def.id
        flagTransform()
    }
    clear("transform_id")
    collision = remove("old_collision") ?: return
}

fun Character.transform(id: String, collision: Boolean = true) {
    if (id.isBlank() || id == "-1") {
        clearTransform()
        return
    }
    this["transform_id"] = id
    val definition = NPCDefinitions.get(id)
    if (this is Player) {
        appearance.apply {
            emote = definition.renderEmote
            transform = definition.id
            size = definition.size
            idleSound = definition.idleSound
            crawlSound = definition.crawlSound
            walkSound = definition.walkSound
            runSound = definition.runSound
            soundDistance = definition.soundDistance
        }
        flagAppearance()
    } else if (this is NPC) {
        visuals.transform.id = definition.id
        flagTransform()
    }
    if (collision) {
        this["old_collision"] = this.collision
        this.collision = CollisionStrategyProvider.get(definition)
    }
}

val Character.transform: String
    get() = this["transform_id", ""]
