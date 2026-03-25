package sim.engine.client.instruction.handle

import com.github.michaelbull.logging.InlineLogger
import sim.cache.definition.Transforms
import sim.engine.client.instruction.InstructionHandler
import sim.engine.client.ui.closeInterfaces
import sim.engine.data.definition.DefinitionsDecoder
import sim.engine.data.definition.ObjectDefinitions
import sim.engine.data.definition.VariableDefinitions
import sim.engine.entity.character.mode.interact.NPCOnObjectInteract
import sim.engine.entity.character.mode.interact.PlayerOnObjectInteract
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player
import sim.engine.entity.obj.GameObject
import sim.engine.entity.obj.GameObjects
import sim.engine.get
import sim.network.client.instruction.InteractObject
import sim.types.Tile

class ObjectOptionHandler : InstructionHandler<InteractObject>() {

    private val logger = InlineLogger()

    override fun validate(player: Player, instruction: InteractObject): Boolean {
        if (player.contains("delay")) {
            return false
        }
        val (objectId, x, y, option) = instruction
        val tile = player.tile.copy(x = x, y = y)
        val target = getObject(tile, objectId)
        if (target == null) {
            logger.warn { "Invalid object $objectId $tile" }
            return false
        }
        val definition = getDefinition(player, ObjectDefinitions, target.def, target.def)
        val options = definition.options
        if (options == null) {
            logger.warn { "Invalid object interaction $target $option ${definition.options.contentToString()}" }
            return false
        }
        val index = option - 1
        val selectedOption = options.getOrNull(index)
        if (selectedOption == null) {
            logger.warn { "Invalid object option $target $index ${options.contentToString()}" }
            return false
        }
        player.closeInterfaces()
        player.interactObject(target, selectedOption)
        return true
    }

    private fun getObject(tile: Tile, objectId: Int): GameObject? {
        val obj = GameObjects.findOrNull(tile, objectId)
        if (obj == null) {
            val definition = ObjectDefinitions.getOrNull(objectId)
            return if (definition == null) {
                GameObjects.findOrNull(tile, objectId.toString())
            } else {
                GameObjects.findOrNull(tile, definition.id)
            }
        }
        return obj
    }

    companion object {
        private fun getVarbitIndex(player: Player, id: Int): Int {
            val definitions: VariableDefinitions = get()
            val key = definitions.getVarbit(id) ?: return 0
            return getInt(definitions, key, player)
        }

        private fun getVarpIndex(player: Player, id: Int): Int {
            val definitions: VariableDefinitions = get()
            val key = definitions.getVarp(id) ?: return 0
            return getInt(definitions, key, player)
        }

        private fun getInt(definitions: VariableDefinitions, key: String, player: Player): Int {
            val variable = definitions.get(key) ?: return 0
            val value = player.variables.get<Any>(key) ?: return 0
            return variable.values.toInt(value)
        }

        fun <T, D : DefinitionsDecoder<T>> getDefinition(player: Player, definitions: D, definition: T, def: Transforms): T {
            val transforms = def.transforms ?: return definition
            val varbit = def.varbit
            if (varbit != -1) {
                val index = getVarbitIndex(player, varbit)
                return definitions.get(transforms.getOrNull(index.coerceAtMost(transforms.lastIndex)) ?: return definition)
            }

            val varp = def.varp
            if (varp != -1) {
                val index = getVarpIndex(player, varp)
                return definitions.get(transforms.getOrNull(index.coerceAtMost(transforms.lastIndex)) ?: return definition)
            }
            return definition
        }
    }
}

fun Player.interactObject(target: GameObject, option: String, approachRange: Int? = null) {
    mode = PlayerOnObjectInteract(target, option, this, approachRange)
}

fun NPC.interactObject(target: GameObject, option: String, approachRange: Int? = null) {
    mode = NPCOnObjectInteract(target, option, this, approachRange)
}