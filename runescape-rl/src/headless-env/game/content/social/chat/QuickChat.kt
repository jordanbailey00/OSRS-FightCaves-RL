package content.social.chat

import com.github.michaelbull.logging.InlineLogger
import content.bot.bot
import content.bot.isBot
import content.social.clan.clan
import content.social.ignore.ignores
import sim.cache.definition.data.QuickChatPhraseDefinition
import sim.cache.definition.data.QuickChatType
import sim.engine.Script
import sim.engine.client.instruction.instruction
import sim.engine.client.message
import sim.engine.client.update.view.Viewport.Companion.VIEW_RADIUS
import sim.engine.client.variable.MapValues
import sim.engine.data.definition.EnumDefinitions
import sim.engine.data.definition.ItemDefinitions
import sim.engine.data.definition.QuickChatPhraseDefinitions
import sim.engine.data.definition.VariableDefinitions
import sim.engine.entity.character.player.*
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.entity.character.player.skill.Skill
import sim.engine.event.AuditLog
import sim.network.client.instruction.QuickChatPrivate
import sim.network.client.instruction.QuickChatPublic
import sim.network.login.protocol.encode.clanQuickChat
import sim.network.login.protocol.encode.privateQuickChatFrom
import sim.network.login.protocol.encode.privateQuickChatTo
import sim.network.login.protocol.encode.publicQuickChat

class QuickChat(
    val phrases: QuickChatPhraseDefinitions,
    val variables: VariableDefinitions,
) : Script {

    val logger = InlineLogger("QuickChat")

    init {
        instruction<QuickChatPrivate> { player ->
            val target = Players.find(friend)
            if (target == null || target.ignores(player)) {
                player.message("Unable to send message - player unavailable.")
                return@instruction
            }
            val definition = phrases.get(file)
            val data = generateData(player, file, data)
            player.client?.privateQuickChatTo(target.name, file, data)
            val text = definition.buildString(EnumDefinitions.definitions, ItemDefinitions.definitions, data)
            AuditLog.event(player, "told_qc", target, text)
            target.client?.privateQuickChatFrom(player.name, player.rights.ordinal, file, data)
        }

        instruction<QuickChatPublic> { player ->
            when (chatType) {
                0 -> {
                    val definition = phrases.get(file)
                    val data = generateData(player, file, data)
                    val text = definition.buildString(EnumDefinitions.definitions, ItemDefinitions.definitions, data)
                    AuditLog.event(player, "said_qc", text)
                    val nearby = Players.filter { it.tile.within(player.tile, VIEW_RADIUS) && !it.ignores(player) }
                    botResponses(definition, player, nearby)
                    nearby.forEach { other ->
                        other.client?.publicQuickChat(player.index, 0x8000, player.rights.ordinal, file, data)
                    }
                }
                1 -> {
                    val clan = player.clan
                    if (clan == null) {
                        player.message("You must be in a clan chat to talk.", ChatType.ClanChat)
                        return@instruction
                    }
                    if (!clan.hasRank(player, clan.talkRank) || !clan.members.contains(player)) {
                        player.message("You are not allowed to talk in this clan chat channel.", ChatType.ClanChat)
                        return@instruction
                    }
                    val definition = phrases.get(file)
                    val data = generateData(player, file, data)
                    val text = definition.buildString(EnumDefinitions.definitions, ItemDefinitions.definitions, data)
                    AuditLog.event(player, "clan_said_qc", clan, text)
                    clan.members.filterNot { it.ignores(player) }.forEach { member ->
                        member.client?.clanQuickChat(player.name, member.clan!!.name, player.rights.ordinal, file, data)
                    }
                }
            }
        }
    }

    private fun botResponses(definition: QuickChatPhraseDefinition, player: Player, players: List<Player>) {
        when (definition.id) {
            // What is your skill level
            0, 7, 12, 15, 22, 29, 33, 40, 46, 54, 61, 69, 73, 78, 84, 91, 95, 102, 104, 110, 115, 119, 126, 134, 141 -> {
                val nearest = players.filter { it.isBot && it != player }.minByOrNull { it.tile.distanceTo(player.tile) } ?: return
                nearest.instructions.trySend(QuickChatPublic(0, definition.id + 1, byteArrayOf()))
            }
            // Combat level
            610 -> {
                val nearest = players.filter { it.isBot && it != player }.minByOrNull { it.tile.distanceTo(player.tile) } ?: return
                nearest.instructions.trySend(QuickChatPublic(0, 952, byteArrayOf()))
            }
            // What are you mining
            130 -> {
                val def = phrases.get(definition.id + 1)
                val type = def.types?.get(0)
                if (type == QuickChatType.MultipleChoice.id) {
                    val nearest = players.filter { it.isBot && it != player }.minByOrNull { it.tile.distanceTo(player.tile) } ?: return
                    val id = def.ids?.get(0)?.get(0) ?: return
                    val enum = EnumDefinitions.get(id)
                    val frame = nearest.bot.frames.peek() ?: return
                    val first = frame.behaviour.produces.firstOrNull { it.startsWith("item:") } ?: return
                    val ore = first.removePrefix("item:").removeSuffix("_ore")
                    val (index, _) = enum.map!!.toList().firstOrNull { it.second == ore } ?: return
                    nearest.instructions.trySend(QuickChatPublic(0, 131, byteArrayOf(0, index.toByte())))
                }
            }
            // TODO will need better enum handling before wanting to add more of these
        }
    }

    fun generateData(player: Player, file: Int, data: ByteArray): ByteArray {
        val definition = phrases.get(file)
        val types = definition.types ?: return data
        if (types.size == 1) {
            when (definition.getType(0)) {
                QuickChatType.SkillLevel -> {
                    val skill = Skill.all[definition.ids!!.first().first()]
                    var level = player.levels.getMax(skill)
                    if (skill == Skill.Constitution) {
                        level /= 10
                    }
                    return byteArrayOf(level.toByte())
                }
                QuickChatType.Varp -> {
                    try {
                        val variable = definition.ids!!.first().first()
                        val key = variables.getVarp(variable)!!
                        val def = variables.get(key)!!
                        val int = def.values.toInt(player[key, def.defaultValue!!])
                        return int(int)
                    } catch (e: Exception) {
                        logger.error(e) { "Quick chat varp: $file ${data.contentToString()}" }
                        return byteArrayOf()
                    }
                }
                QuickChatType.Varbit -> {
                    try {
                        val variable = definition.ids!!.first().first()
                        val key = variables.getVarbit(variable)!!
                        val def = variables.get(key)!!
                        val int = def.values.toInt(player[key, def.defaultValue!!])
                        return int(int)
                    } catch (e: Exception) {
                        logger.error(e) { "Quick chat varbit: $file ${data.contentToString()}" }
                        return byteArrayOf()
                    }
                }
                QuickChatType.CombatLevel -> return byteArrayOf(player.combatLevel.toByte())
                QuickChatType.SlayerAssignment -> {
                    val int = (variables.get("slayer_target")!!.values as MapValues).values[player["slayer_target"]] ?: 0
                    return int(int)
                }
                QuickChatType.ClanRank,
                QuickChatType.AverageCombatLevel,
                QuickChatType.SoulWars,
                -> return byteArrayOf(0)
                else -> return data
            }
        } else {
            val list = mutableListOf<Byte>()
            for (index in types.indices) {
                when (definition.getType(index)) {
                    QuickChatType.SkillLevel, QuickChatType.SkillExperience -> {
                        val skill = Skill.all[definition.ids!![index].last()]
                        list.add(player.levels.getMax(skill).toByte())
                    }
                    else -> return data
                }
            }
            return list.map { it }.toByteArray()
        }
    }

    fun int(value: Int) = byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())
}
