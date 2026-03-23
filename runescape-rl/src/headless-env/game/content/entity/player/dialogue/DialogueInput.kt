package content.entity.player.dialogue

import sim.engine.Script
import sim.engine.client.instruction.instruction
import sim.engine.client.sendScript
import sim.engine.client.ui.closeDialogue
import sim.engine.suspend.IntSuspension
import sim.engine.suspend.NameSuspension
import sim.engine.suspend.StringSuspension
import sim.network.client.instruction.EnterInt
import sim.network.client.instruction.EnterName
import sim.network.client.instruction.EnterString

class DialogueInput : Script {

    init {
        continueDialogue("dialogue_npc_chat*:continue") {
            continueDialogue()
        }

        continueDialogue("dialogue_chat*:continue") {
            continueDialogue()
        }

        continueDialogue("dialogue_message*:continue") {
            continueDialogue()
        }

        continueDialogue("dialogue_level_up:continue") {
            closeDialogue()
        }

        continueDialogue("dialogue_obj_box:continue") {
            continueDialogue()
        }

        continueDialogue("dialogue_double_obj_box:continue") {
            continueDialogue()
        }

        continueDialogue("dialogue_multi*:line*") {
            val choice = it.substringAfter(":line").toIntOrNull() ?: -1
            (dialogueSuspension as? IntSuspension)?.resume(choice)
        }

        instruction<EnterInt> { player ->
            (player.dialogueSuspension as? IntSuspension)?.resume(value)
            player.sendScript("close_entry")
        }

        instruction<EnterString> { player ->
            (player.dialogueSuspension as? StringSuspension)?.resume(value)
            player.sendScript("close_entry")
        }

        instruction<EnterName> { player ->
            (player.dialogueSuspension as? NameSuspension)?.resume(value)
            player.sendScript("close_entry")
        }

        continueDialogue("dialogue_confirm_destroy:*") {
            (dialogueSuspension as? StringSuspension)?.resume(it.substringAfter(":"))
        }

        continueDialogue("dialogue_skill_creation:choice*") {
            val choice = it.substringAfter(":choice").toIntOrNull() ?: 0
            (dialogueSuspension as? IntSuspension)?.resume(choice - 1)
            closeDialogue()
        }
    }
}
