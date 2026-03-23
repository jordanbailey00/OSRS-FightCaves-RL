package content.area.kandarin.ourania

import com.github.michaelbull.logging.InlineLogger
import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.variable.start
import sim.engine.data.definition.EnumDefinitions
import sim.engine.entity.character.player.chat.ChatType
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.exp.exp
import sim.engine.entity.character.sound
import sim.engine.entity.item.drop.DropTables
import sim.engine.entity.item.drop.ItemDrop
import sim.engine.inv.inventory
import sim.engine.inv.transact.TransactionError
import sim.engine.inv.transact.operation.AddItem.add
import sim.engine.inv.transact.operation.RemoveItemLimit.removeToLimit
import sim.types.random

class OuraniaAltar(val drops: DropTables) : Script {
    val logger = InlineLogger()

    init {
        objectOperate("Craft-rune", "ourania_altar") {
            val level = levels.get(Skill.Runecrafting)
            val table = drops.get("ourania_rune_table_level_${if (level >= 99) 10 else level / 10}") ?: return@objectOperate
            var experience = 0.0
            var usedArdougneCloak = false
            inventory.transaction {
                val essence = removeToLimit("pure_essence", 28)
                if (essence == 0) {
                    error = TransactionError.Deficient()
                    return@transaction
                }
                val runes = mutableListOf<ItemDrop>()
                for (i in 0 until essence) {
                    table.roll(list = runes)
                }
                for (drop in runes) {
                    val item = drop.toItem()
                    val doubleChance = EnumDefinitions.int("runecrafting_ourania_double_chance", item.id)
                    val xp = EnumDefinitions.int("runecrafting_xp", item.id) / 10.0
                    val amount = if (get("ardougne_medium_diary_complete", false) && random.nextDouble(100.0) <= doubleChance) 2 else 1
                    usedArdougneCloak = usedArdougneCloak || amount == 2
                    add(item.id, amount)
                    experience += xp * 2.0
                }
            }
            start("movement_delay", 3)
            when (inventory.transaction.error) {
                is TransactionError.Deficient, is TransactionError.Invalid -> {
                    message("You don't have any pure essences to bind.")
                }
                TransactionError.None -> {
                    exp(Skill.Runecrafting, experience)
                    anim("bind_runes")
                    gfx("bind_runes")
                    sound("bind_runes")
                    message("You bind the temple's power into runes.", ChatType.Filter)
                    if (usedArdougneCloak) {
                        message("Your Ardougne cloak seems to shimmer with power.", ChatType.Filter)
                    }
                }
                else -> logger.warn { "Error binding runes $this ${levels.get(Skill.Runecrafting)} $experience" }
            }
        }

        itemOnObjectOperate("*_talisman", "ourania_altar") {
            message("Your talisman has no effect on the altar.")
        }
    }
}
