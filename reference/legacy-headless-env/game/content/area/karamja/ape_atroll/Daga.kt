package content.area.karamja.ape_atroll

import content.entity.npc.shop.openShop
import content.entity.player.dialogue.Happy
import content.entity.player.dialogue.Neutral
import content.entity.player.dialogue.Quiz
import content.entity.player.dialogue.Shifty
import content.entity.player.dialogue.type.choice
import content.entity.player.dialogue.type.npc
import content.entity.player.dialogue.type.player
import sim.engine.Script
import sim.engine.entity.character.player.chat.inventoryFull
import sim.engine.entity.character.player.equip.equipped
import sim.engine.inv.inventory
import sim.engine.inv.transact.TransactionError
import sim.engine.inv.transact.operation.AddItem.add
import sim.engine.inv.transact.operation.RemoveItem.remove
import sim.network.login.protocol.visual.update.player.EquipSlot

class Daga : Script {

    init {

        npcOperate("Talk-to", "daga") {

            val amulet = equipped(EquipSlot.Amulet)

            if (amulet.id == "monkeyspeak_amulet") {

                npc<Shifty>("Sorry, you don't have enough space in your inventory.")

                choice {
                    option<Neutral>("Yes, please.") {
                        openShop("dagas_scimitar_smithy")
                    }
                    option<Neutral>("No, thanks.") {
                    }
                    option<Quiz>("Do you have any Dragon Scimitars in stock?") {
                        npc<Happy>("It just so happens I recently got a fresh delivery. <br>Do you want to buy one?")
                        choice {
                            option("Yes.") {
                                player<Neutral>("Yes, please.")
                                inventory.transaction {
                                    remove("coin", 100_000)
                                    add("dragon_scimitar")
                                }
                                when (inventory.transaction.error) {
                                    is TransactionError.Full -> {
                                        inventoryFull()
                                        npc<Shifty>("Sorry, you don't have enough space in your inventory.")
                                    }

                                    TransactionError.None -> {
                                        npc<Shifty>("There you go. Pleasure doing business with you.")
                                    }

                                    else -> npc<Shifty>(
                                        "Sorry, you don't have enough coins. <br>It costs 100,000 gold coins.",
                                    )
                                }

                                option("No.") {
                                    player<Shifty>("No.")
                                }
                            }
                        }
                    }
                }
            } else {
                npc<Shifty>("Ook! Ah Uh Ah! Ook Ook! Ah!")
            }
        }

        npcOperate("Trade", "daga") {
            val amulet = equipped(EquipSlot.Amulet)
            if (amulet.id == "monkeyspeak_amulet") {
                openShop("dagas_scimitar_smithy")
            } else {
                npc<Shifty>("Ook! Ah Uh Ah! Ook Ook! Ah!")
            }
        }
    }
}
