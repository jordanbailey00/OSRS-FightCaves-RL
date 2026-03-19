package content.social.chat

import content.social.clan.chatType
import content.social.clan.clan
import content.social.ignore.ignores
import net.pearx.kasechange.toTitleCase
import sim.cache.secure.Huffman
import sim.engine.Script
import sim.engine.client.instruction.instruction
import sim.engine.client.message
import sim.engine.client.update.view.Viewport.Companion.VIEW_RADIUS
import sim.engine.entity.character.player.Players
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.entity.character.player.name
import sim.engine.entity.character.player.rights
import sim.engine.event.AuditLog
import sim.network.client.instruction.ChatPrivate
import sim.network.client.instruction.ChatPublic
import sim.network.client.instruction.ChatTypeChange
import sim.network.login.protocol.encode.clanChat
import sim.network.login.protocol.encode.privateChatFrom
import sim.network.login.protocol.encode.privateChatTo
import sim.network.login.protocol.encode.publicChat

class Chat(val huffman: Huffman) : Script {

    init {
        instruction<ChatPrivate> { player ->
            val target = Players.find(friend)
            if (target == null || target.ignores(player)) {
                player.message("Unable to send message - player unavailable.")
                return@instruction
            }
            AuditLog.event(player, "told", target, message)
            val compressed = huffman.compress(message)
            player.client?.privateChatTo(target.name, compressed)
            target.client?.privateChatFrom(player.name, player.rights.ordinal, compressed)
        }

        instruction<ChatTypeChange> { player ->
            player["chat_type"] = when (type) {
                1 -> "clan"
                else -> "public"
            }
        }

        instruction<ChatPublic> { player ->
            val text = if (text.all { it.isUpperCase() }) {
                text.toTitleCase()
            } else {
                text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }

            when (player.chatType) {
                "public" -> {
                    AuditLog.event(player, "said", text)
                    val compressed = huffman.compress(text)
                    Players.filter { it.tile.within(player.tile, VIEW_RADIUS) && !it.ignores(player) }.forEach {
                        it.client?.publicChat(player.index, effects, player.rights.ordinal, compressed)
                    }
                }
                "clan" -> {
                    val clan = player.clan
                    if (clan == null) {
                        player.message("You must be in a clan chat to talk.", ChatType.ClanChat)
                        return@instruction
                    }
                    if (!clan.hasRank(player, clan.talkRank) || !clan.members.contains(player)) {
                        player.message("You are not allowed to talk in this clan chat.", ChatType.ClanChat)
                        return@instruction
                    }
                    AuditLog.event(player, "clan_said", clan, text)
                    val compressed = huffman.compress(text)
                    clan.members.filterNot { it.ignores(player) }.forEach { member ->
                        member.client?.clanChat(player.name, member.clan!!.name, player.rights.ordinal, compressed)
                    }
                }
            }
        }
    }
}
