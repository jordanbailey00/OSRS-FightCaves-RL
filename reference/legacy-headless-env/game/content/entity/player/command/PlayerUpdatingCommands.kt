package content.entity.player.command

import content.bot.isBot
import content.entity.proj.shoot
import net.pearx.kasechange.toScreamingSnakeCase
import sim.engine.Script
import sim.engine.client.command.adminCommand
import sim.engine.client.command.commandAlias
import sim.engine.client.command.intArg
import sim.engine.client.command.playerCommand
import sim.engine.client.command.stringArg
import sim.engine.client.message
import sim.engine.data.definition.AnimationDefinitions
import sim.engine.data.definition.GraphicDefinitions
import sim.engine.entity.character.colourOverlay
import sim.engine.entity.character.flagExactMovement
import sim.engine.entity.character.player.*
import sim.engine.entity.character.setTimeBar
import sim.types.Delta
import sim.types.Direction

class PlayerUpdatingCommands(
    animationDefinitions: AnimationDefinitions,
    graphicDefinitions: GraphicDefinitions,
) : Script {

    init {
        adminCommand("kill", desc = "Remove all bots") {
            val iter = Players.iterator()
            val remove = mutableListOf<Player>()
            while (iter.hasNext()) {
                val p = iter.next()
                if (p.isBot) {
                    remove.add(p)
                }
            }
            for (bot in remove) {
                Players.remove(bot)
            }
        }

        playerCommand("players", desc = "Get the total and local player counts") {
            message("Players: ${Players.size}, local: ${viewport?.players?.localCount ?: 0}, bots: ${Players.count { it.isBot }}")
        }

        adminCommand("anim", stringArg("anim-id", autofill = animationDefinitions.ids.keys), desc = "Perform animation (-1 to clear)") { args ->
            when (args[0]) {
                "-1", "" -> clearAnim()
                else -> anim(args[0], override = true) // 863
            }
        }

        adminCommand("emote", stringArg("emote-id"), desc = "Perform render emote (-1 to clear)") { args ->
            when (args[0]) {
                "-1", "" -> clearRenderEmote()
                else -> renderEmote(args[0])
            }
        }

        adminCommand("gfx", stringArg("gfx-id", autofill = graphicDefinitions.ids.keys), desc = "Perform graphic effect (-1 to clear)") { args ->
            when (args[0]) {
                "-1", "" -> clearGfx()
                else -> gfx(args[0]) // 93
            }
        }

        adminCommand("proj", stringArg("gfx-id", autofill = graphicDefinitions.ids.keys), desc = "Shoot projectile (-1 to clear)") { args ->
            shoot(args[0], tile.add(0, 5), delay = 0, flightTime = 400)
        }
        commandAlias("proj", "shoot")

        adminCommand("overlay") {
            colourOverlay(-2108002746, 10, 100)
        }

        adminCommand("exact_move", intArg("start-x", optional = true), intArg("start-y", optional = true), intArg("end-x", optional = true), intArg("end-y", optional = true)) { args ->
            val move = visuals.exactMovement
            move.startX = args.getOrNull(0)?.toIntOrNull() ?: -4
            move.startY = args.getOrNull(0)?.toIntOrNull() ?: 2
            move.startDelay = 0
            move.endX = args.getOrNull(0)?.toIntOrNull() ?: 0
            move.endY = args.getOrNull(0)?.toIntOrNull() ?: 0
            move.endDelay = 100
            move.direction = Direction.EAST.ordinal
            flagExactMovement()
        }
        commandAlias("move", "exact_move")

        adminCommand("time") {
            setTimeBar(true, 0, 60, 1)
        }

        adminCommand("face", intArg("delta-x"), intArg("delta-y"), desc = "Turn player to face a direction or delta coordinate") { args ->
            if (args[0].contains(" ")) {
                val parts = args[0].split(" ")
                face(Delta(parts[0].toInt(), parts[1].toInt()))
            } else {
                val direction = Direction.valueOf(args[0].toScreamingSnakeCase())
                face(direction.delta)
            }
        }

        adminCommand("skill_level", intArg("level"), desc = "Set the current displayed skill level") { args ->
            skillLevel = args[0].toInt()
        }

        adminCommand("combat_level", intArg("level"), desc = "Set the current displayed combat level") { args ->
            combatLevel = args[0].toInt()
        }

        adminCommand("toggle_skill_level", desc = "Toggle skill level display") {
            toggleSkillLevel()
        }

        adminCommand("summoning_level", intArg("level"), desc = "Set the current summoning combat level") { args ->
            summoningCombatLevel = args[0].toInt()
        }
    }
}
