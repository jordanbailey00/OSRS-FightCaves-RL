package content.entity.player.dialogue

import sim.engine.client.ui.Interfaces
import sim.engine.data.definition.AnimationDefinitions
import sim.engine.entity.character.player.Player
import sim.engine.get
import sim.engine.suspend.ContinueSuspension

fun Interfaces.sendLines(id: String, lines: List<String>) {
    for ((index, line) in lines.withIndex()) {
        sendText(id, "line${index + 1}", line)
    }
}

fun Interfaces.sendChat(
    id: String,
    component: String,
    expression: String,
    title: String,
    lines: List<String>,
) {
    val animationDefs: AnimationDefinitions = get()
    val definition = animationDefs.getOrNull("expression_$expression${lines.size}") ?: animationDefs.get("expression_$expression")
    sendAnimation(id, component, definition.id)
    sendText(id, "title", title)
    sendLines(id, lines)
}

fun Player.continueDialogue() {
    (dialogueSuspension as? ContinueSuspension)?.resume(Unit)
}
