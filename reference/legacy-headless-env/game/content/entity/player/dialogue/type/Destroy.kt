package content.entity.player.dialogue.type

import sim.engine.client.ui.close
import sim.engine.client.ui.open
import sim.engine.data.definition.ItemDefinitions
import sim.engine.entity.character.player.Player
import sim.engine.suspend.StringSuspension

private const val DESTROY_INTERFACE_ID = "dialogue_confirm_destroy"

suspend fun Player.destroy(item: String, text: String): Boolean {
    check(open(DESTROY_INTERFACE_ID)) { "Unable to open destroy dialogue for $item $this" }
    interfaces.sendText(DESTROY_INTERFACE_ID, "line1", text.trimIndent().replace("\n", "<br>"))
    val def = ItemDefinitions.get(item)
    interfaces.sendText(DESTROY_INTERFACE_ID, "item_name", def.name)
    interfaces.sendItem(DESTROY_INTERFACE_ID, "item_slot", def.id, 1)
    val result = StringSuspension.get(this) == "confirm"
    close(DESTROY_INTERFACE_ID)
    return result
}
