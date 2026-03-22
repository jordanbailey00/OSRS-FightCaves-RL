package content.entity.player.dialogue.type

import content.entity.player.dialogue.Expression
import content.entity.player.dialogue.sendChat
import net.pearx.kasechange.toSnakeCase
import sim.engine.client.ui.close
import sim.engine.client.ui.open
import sim.engine.data.definition.FontDefinitions
import sim.engine.data.definition.InterfaceDefinitions
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.name
import sim.engine.get
import sim.engine.suspend.ContinueSuspension
import sim.network.login.protocol.encode.playerDialogueHead

suspend inline fun <reified E : Expression> Player.player(text: String, largeHead: Boolean = false, clickToContinue: Boolean = true, title: String? = null) {
    val expression = E::class.simpleName!!.toSnakeCase()
    player(expression, text, largeHead, clickToContinue, title)
}

suspend fun Player.player(expression: String, text: String, largeHead: Boolean = false, clickToContinue: Boolean = true, title: String? = null) {
    val lines = if (text.contains("\n")) text.trimIndent().lines() else get<FontDefinitions>().get("q8_full").splitLines(text, 380)
    check(lines.size <= 4) { "Maximum player chat lines exceeded ${lines.size} for $this" }
    val id = getInterfaceId(lines.size, clickToContinue)
    check(open(id)) { "Unable to open player dialogue for $this" }
    val head = getChatHeadComponentName(largeHead)
    sendPlayerHead(this, id, head)
    interfaces.sendChat(id, head, expression, title ?: name, lines)
    if (clickToContinue) {
        ContinueSuspension.get(this)
        close(id)
    }
}

private fun getChatHeadComponentName(large: Boolean): String = "head${if (large) "_large" else ""}"

private fun getInterfaceId(lines: Int, prompt: Boolean): String = "dialogue_chat${if (!prompt) "_np" else ""}$lines"

private fun sendPlayerHead(player: Player, id: String, component: String) {
    val comp = InterfaceDefinitions.getComponent(id, component) ?: return
    player.client?.playerDialogueHead(comp.id)
}
