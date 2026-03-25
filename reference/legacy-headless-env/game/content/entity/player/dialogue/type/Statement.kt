package content.entity.player.dialogue.type

import content.entity.player.dialogue.sendLines
import sim.engine.client.ui.close
import sim.engine.client.ui.open
import sim.engine.data.definition.FontDefinitions
import sim.engine.entity.character.player.Player
import sim.engine.get
import sim.engine.suspend.ContinueSuspension

private const val MAXIMUM_STATEMENT_SIZE = 5

suspend fun Player.statement(text: String, clickToContinue: Boolean = true) {
    val lines = if (text.isBlank()) {
        listOf("")
    } else if (text.contains("\n")) {
        text.trimIndent().lines()
    } else {
        get<FontDefinitions>().get("q8_full").splitLines(text, 470)
    }
    check(lines.size <= MAXIMUM_STATEMENT_SIZE) { "Maximum statement lines exceeded ${lines.size} for $this" }
    val id = getInterfaceId(lines.size, clickToContinue)
    check(open(id)) { "Unable to open statement dialogue $id for $this" }
    interfaces.sendLines(id, lines)
    if (clickToContinue) {
        ContinueSuspension.get(this)
        close(id)
    }
}

private fun getInterfaceId(lines: Int, prompt: Boolean): String = "dialogue_message${if (!prompt) "_np" else ""}$lines"
