package content.entity.player.dialogue.type

import sim.engine.client.sendScript
import sim.engine.client.ui.close
import sim.engine.client.ui.open
import sim.engine.data.definition.FontDefinitions
import sim.engine.data.definition.ItemDefinitions
import sim.engine.entity.character.player.Player
import sim.engine.get
import sim.engine.suspend.ContinueSuspension

private const val ITEM_INTERFACE_ID = "dialogue_obj_box"
private const val DOUBLE_ITEM_INTERFACE_ID = "dialogue_double_obj_box"

suspend fun Player.item(item: String, zoom: Int, text: String, sprite: Int? = null) {
    check(open(ITEM_INTERFACE_ID)) { "Unable to open item dialogue for $this" }
    sendScript("dialogue_item_zoom", ItemDefinitions.get(item).id, zoom)
    if (sprite != null) {
        interfaces.sendSprite(ITEM_INTERFACE_ID, "sprite", sprite)
    }
    val lines = if (text.contains("\n")) text.trimIndent().replace("\n", "<br>") else get<FontDefinitions>().get("q8_full").splitLines(text, 380).joinToString("<br>")
    interfaces.sendText(ITEM_INTERFACE_ID, "line1", lines)
    ContinueSuspension.get(this)
    close(ITEM_INTERFACE_ID)
}

suspend fun Player.item(sprite: Int, text: String) {
    check(open(ITEM_INTERFACE_ID)) { "Unable to open item dialogue for $this" }
    interfaces.sendSprite(ITEM_INTERFACE_ID, "sprite", sprite)
    val lines = if (text.contains("\n")) text.trimIndent().replace("\n", "<br>") else get<FontDefinitions>().get("q8_full").splitLines(text, 380).joinToString("<br>")
    interfaces.sendText(ITEM_INTERFACE_ID, "line1", lines)
    ContinueSuspension.get(this)
    close(ITEM_INTERFACE_ID)
}

suspend fun Player.items(item1: String, item2: String, text: String) {
    check(open(DOUBLE_ITEM_INTERFACE_ID)) { "Unable to open item dialogue for $this" }
    interfaces.sendItem(DOUBLE_ITEM_INTERFACE_ID, "model1", ItemDefinitions.get(item1).id)
    interfaces.sendItem(DOUBLE_ITEM_INTERFACE_ID, "model2", ItemDefinitions.get(item2).id)
    val lines = if (text.contains("\n")) text.trimIndent().replace("\n", "<br>") else get<FontDefinitions>().get("q8_full").splitLines(text, 380).joinToString("<br>")
    interfaces.sendText(DOUBLE_ITEM_INTERFACE_ID, "line1", lines)
    ContinueSuspension.get(this)
    close(DOUBLE_ITEM_INTERFACE_ID)
}
