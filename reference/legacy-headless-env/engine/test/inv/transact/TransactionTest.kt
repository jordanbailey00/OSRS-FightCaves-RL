package sim.engine.inv.transact

import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.Test
import sim.engine.entity.character.player.Player
import sim.engine.entity.item.Item
import sim.engine.inv.Inventory
import sim.engine.inv.InventoryApi
import sim.engine.inv.transact.operation.TransactionOperationTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionTest : TransactionOperationTest() {

    @Test
    fun `Set tracks changes`() {
        mockkObject(InventoryApi)
        val inventory = Inventory.debug(1)
        val player: Player = mockk(relaxed = true)
        val transaction = inventory.transaction
        transaction.changes.bind(player)
        transaction.set(0, Item("item", 1))
        transaction.changes.send()
        verify { InventoryApi.changed(player, any()) }
        unmockkObject(InventoryApi)
    }

    @Test
    fun `Link second inventory to transaction`() {
        val inventory = Inventory.debug(1)
        val transaction = inventory.transaction
        val inventory2 = Inventory.debug(1)
        transaction.start()

        assertFalse(inventory2.transaction.state.hasSaved())
        transaction.link(inventory2)
        assertTrue(transaction.linked(inventory2.transaction))
        assertTrue(inventory2.transaction.state.hasSaved())
        assertFalse(transaction.failed)
        assertTrue(transaction.commit())
    }

    @Test
    fun `Error in linked inventory fails main transaction`() {
        val inventory = Inventory.debug(1)
        val transaction = inventory.transaction
        val inventory2 = Inventory.debug(1)
        transaction.start()
        val transaction2 = transaction.link(inventory2)
        transaction2.error = TransactionError.Invalid
        assertTrue(transaction.failed)
        assertFalse(transaction.commit())
        assertEquals(TransactionError.Invalid, transaction.error)
    }

    @Test
    fun `Can't link with inventory in a transaction`() {
        val inventory = Inventory.debug(1)
        val transaction = inventory.transaction
        val inventory2 = Inventory.debug(1)
        val transaction2 = inventory2.transaction
        transaction2.start()
        transaction.link(inventory2)
        assertFalse(inventory.transaction.linked(transaction))
        assertEquals(TransactionError.Invalid, transaction.error)
    }

    @Test
    fun `Link transaction with itself does nothing`() {
        val inventory = Inventory.debug(1)
        val transaction = inventory.transaction
        transaction.link(inventory)
        assertFalse(inventory.transaction.linked(transaction))
        assertEquals(TransactionError.None, transaction.error)
    }
}
