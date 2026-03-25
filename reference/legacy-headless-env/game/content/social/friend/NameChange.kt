package content.social.friend

import content.entity.player.dialogue.type.choice
import content.entity.player.dialogue.type.nameEntry
import sim.engine.Script
import sim.engine.client.command.modCommand
import sim.engine.client.message
import sim.engine.client.ui.chat.plural
import sim.engine.client.variable.remaining
import sim.engine.client.variable.start
import sim.engine.data.Settings
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.Players
import sim.engine.entity.character.player.isAdmin
import sim.engine.entity.character.player.name
import sim.engine.queue.strongQueue
import sim.engine.timer.epochSeconds
import sim.network.login.protocol.encode.Friend
import java.util.concurrent.TimeUnit

class NameChange : Script {

    init {
        modCommand("rename", desc = "Change your display name (login stays the same)", handler = ::rename)
    }

    fun rename(player: Player, args: List<String>) {
        val remaining = player.remaining("rename_delay", epochSeconds()).toLong()
        if (remaining > 0 && !player.isAdmin()) {
            player.message("You've already changed your name this month.")
            val days = TimeUnit.SECONDS.toDays(remaining)
            val hours = TimeUnit.SECONDS.toHours(remaining).rem(24)
            player.message("You can change your name again in $days ${"day".plural(days)} and $hours ${"hour".plural(hours)}.")
            return
        }
        player.strongQueue("rename") {
            val toName = player.nameEntry("Enter a new name")
            if (toName.length !in 1..12) {
                player.message("Name too long, a username must be less than 12 characters.")
                return@strongQueue
            }
            player.choice("Change your name to '$toName'?") {
                option("Yes, call me $toName") {
                    val previous = player.name
                    player.name = toName
                    Players
                        .filter { it.friend(player) }
                        .forEach { friend ->
                            friend.updateFriend(Friend(toName, previous, renamed = true, world = Settings.world, worldName = Settings.worldName))
                        }
                    player.message("Your name has been successfully changed to '$toName'.")
                    player.message("You can change your name again in 30 days.")
                    player.start("rename_delay", TimeUnit.DAYS.toSeconds(30).toInt(), epochSeconds())
                }
                option("No, I like my current name")
            }
        }
    }
}
