package sim.engine

import com.github.michaelbull.logging.InlineLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sim.engine.client.ui.InterfaceApi
import sim.engine.client.ui.dialogue.Dialogues
import sim.engine.client.variable.VariableApi
import sim.engine.data.SettingsReload
import sim.engine.entity.*
import sim.engine.entity.character.Death
import sim.engine.entity.character.mode.combat.CombatApi
import sim.engine.entity.character.mode.combat.CombatMovement
import sim.engine.entity.character.mode.move.Moved
import sim.engine.entity.character.npc.hunt.Hunt
import sim.engine.entity.character.player.Teleport
import sim.engine.entity.character.player.skill.Skills
import sim.engine.inv.InventoryApi
import sim.engine.inv.Items
import sim.engine.timer.TimerApi
import kotlin.coroutines.cancellation.CancellationException

/**
 * A helper interface made up of all callable methods for easier scripting.
 * Scripts are automatically detected and inject parameters @see ContentLoader.kt
 */
interface Script : Spawn, Despawn, Skills, Moved, VariableApi, TimerApi, Operation, Approachable, InterfaceApi, Death, SettingsReload, Dialogues, Items, InventoryApi, Hunt, Teleport, CombatApi {
    companion object {

        private val logger = InlineLogger()
        private val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)

        fun launch(block: suspend CoroutineScope.() -> Unit) {
            scope.launch(errorHandler, block = block)
        }
        private val errorHandler = CoroutineExceptionHandler { _, throwable ->
            if (throwable !is CancellationException) {
                logger.warn(throwable) { "Error in script handler." }
            }
        }

        val interfaces: MutableList<AutoCloseable> = mutableListOf(
            Spawn,
            Despawn,
            Skills,
            Moved,
            VariableApi,
            TimerApi,
            Operation,
            Approachable,
            InterfaceApi,
            Death,
            SettingsReload,
            Dialogues,
            Items,
            InventoryApi,
            Hunt,
            Teleport,
            CombatApi,
            CombatMovement,
        )

        fun clear() {
            for (closable in interfaces) {
                closable.close()
            }
        }
    }
}