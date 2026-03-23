package content.entity.player.command

import content.entity.effect.transform
import sim.engine.Script
import sim.engine.client.command.adminCommand
import sim.engine.client.command.intArg
import sim.engine.client.command.stringArg
import sim.engine.data.definition.AnimationDefinitions
import sim.engine.data.definition.GraphicDefinitions
import sim.engine.data.definition.NPCDefinitions
import sim.engine.entity.character.colourOverlay
import sim.engine.entity.character.flagHits
import sim.engine.entity.character.move.running
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.setTimeBar
import sim.network.login.protocol.visual.update.HitSplat
import sim.types.Delta

class NPCUpdatingCommands(
    animationDefinitions: AnimationDefinitions,
    graphicDefinitions: GraphicDefinitions,
) : Script {

    init {
        adminCommand("npc_tfm", stringArg("transform-id", autofill = NPCDefinitions.ids.keys)) { args ->
            val npc = NPCs.at(tile.addY(1)).first()
            npc.transform(args[0])
        }

        adminCommand("npc_turn", intArg("delta-x"), intArg("delta-y")) { args ->
            val npc = NPCs.at(tile.addY(1)).first()
            npc.face(Delta(args[0].toInt(), args[1].toInt()))
        }

        adminCommand("npc_anim", stringArg("anim-id", autofill = animationDefinitions.ids.keys)) { args ->
            val npc = NPCs.at(tile.addY(1)).first()
            npc.anim(args[0]) // 863
        }

        adminCommand("npc_overlay") { _ ->
            val npc = NPCs.at(tile.addY(1)).first()
            npc.colourOverlay(-2108002746, 10, 100)
        }

        adminCommand("npc_chat", stringArg("message", optional = true)) { args ->
            val npc = NPCs.at(tile.addY(1)).first()
            npc.say(args.getOrNull(0) ?: "Testing")
        }

        adminCommand("npc_gfx", stringArg("gfx-id", autofill = graphicDefinitions.ids.keys)) { args ->
            val npc = NPCs.at(tile.addY(1)).first()
            npc.gfx(args[0]) // 93
        }

        adminCommand("npc_hit") { _ ->
            val npc = NPCs.at(tile.addY(1)).first()
            npc.visuals.hits.add(HitSplat(10, HitSplat.Mark.Healed, npc.levels.getPercent(Skill.Constitution, fraction = 255.0).toInt()))
            npc.flagHits()
        }

        adminCommand("npc_time") { _ ->
            val npc = NPCs.at(tile.addY(1)).first()
            npc.setTimeBar(true, 0, 60, 1)
        }

        adminCommand("npc_watch") { _ ->
            val npc = NPCs.at(tile.addY(1)).first()
            npc.watch(this)
        }

        adminCommand("npc_crawl") { _ ->
            val npc = NPCs.at(tile.addY(1)).first()
            //    npc.def["crawl"] = true
            //    npc.walkTo(npc.tile)
            //    npc.movement.steps.add(Tile(npc.tile.x, npc.tile.y + 1))
        }

        adminCommand("npc_run") { _ ->
            val npc = NPCs.at(tile.addY(1)).first()
            npc.running = true
            //    npc.walkTo(npc.tile)
            //    npc.movement.steps.add(Tile(npc.tile.x, npc.tile.y + 2))
        }
    }
}
