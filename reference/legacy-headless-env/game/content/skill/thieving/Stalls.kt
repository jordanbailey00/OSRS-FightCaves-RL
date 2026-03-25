package content.skill.thieving

import content.entity.combat.underAttack
import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.ui.chat.toIntRange
import sim.engine.client.variable.hasClock
import sim.engine.client.variable.start
import sim.engine.entity.character.player.chat.inventoryFull
import sim.engine.entity.character.player.equip.equipped
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.exp.exp
import sim.engine.entity.character.player.skill.level.Level
import sim.engine.entity.character.player.skill.level.Level.has
import sim.engine.entity.item.drop.DropTables
import sim.engine.entity.obj.replace
import sim.engine.inv.inventory
import sim.engine.inv.transact.TransactionError
import sim.engine.inv.transact.operation.AddItem.add
import sim.engine.timer.toTicks
import sim.network.login.protocol.visual.update.player.EquipSlot
import sim.types.random
import java.util.concurrent.TimeUnit

class Stalls(val drops: DropTables) : Script {

    init {
        objectOperate("Steal-from", "*_stall") { (target) ->
            if (underAttack) {
                message("You can't do this while you're in combat.")
                return@objectOperate
            }
            if (equipped(EquipSlot.Weapon).id.endsWith("_greegree")) {
                message("You wouldn’t like to blow your cover by getting caught stealing.")
                return@objectOperate
            }
            val def = target.def(this)
            val level: Int = def.getOrNull("level") ?: return@objectOperate
            if (!has(Skill.Thieving, level, " to steal from this stall")) {
                return@objectOperate
            }
            if (inventory.isFull()) {
                inventoryFull()
                return@objectOperate
            }
            start("thieving", 2)
            set("stall_level", level)
            anim("take")
            delay(2)
            val chance: String? = def.getOrNull("chance")
            if (chance != null) {
                val range = chance.toIntRange(inclusive = true)
                if (!Level.success(levels.get(Skill.Thieving), range)) {
                    message("You attempt to steal from the stall but you miss your chance...")
                    return@objectOperate
                }
            }
            if (!hasClock("${target.id}_thief")) {
                start("${target.id}_thief", TimeUnit.MINUTES.toTicks(random.nextInt(20, 30)))
            }
            val table = drops.get("${target.id}_drop_table")
            if (table != null) {
                val drops = table.roll().map { it.toItem() }
                inventory.transaction {
                    for (item in drops) {
                        add(item.id, item.amount)
                    }
                }
                when (inventory.transaction.error) {
                    is TransactionError.Full -> {
                        inventoryFull()
                        return@objectOperate
                    }
                    TransactionError.None -> for (item in drops) {
                        Stole.stole(this, target, item)
                    }
                    else -> {}
                }
            }
            val exp: Double = def.getOrNull("exp") ?: return@objectOperate
            exp(Skill.Thieving, exp)
            val restock: Int = def.getOrNull("restock") ?: return@objectOperate
            target.replace("${target.id}_empty", ticks = restock)
        }
    }
}
