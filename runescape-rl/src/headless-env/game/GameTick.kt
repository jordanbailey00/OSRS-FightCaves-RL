import com.github.michaelbull.logging.InlineLogger
import content.bot.BotManager
import content.social.trade.exchange.GrandExchange
import sim.engine.client.instruction.InstructionHandlers
import sim.engine.client.instruction.InstructionTask
import sim.engine.client.update.CharacterTask
import sim.engine.client.update.CharacterUpdateTask
import sim.engine.client.update.NPCTask
import sim.engine.client.update.PlayerTask
import sim.engine.client.update.batch.ZoneBatchUpdates
import sim.engine.client.update.iterator.ParallelIterator
import sim.engine.client.update.iterator.SequentialIterator
import sim.engine.client.update.iterator.TaskIterator
import sim.engine.client.update.npc.NPCResetTask
import sim.engine.client.update.npc.NPCUpdateTask
import sim.engine.client.update.player.PlayerResetTask
import sim.engine.client.update.player.PlayerUpdateTask
import sim.engine.data.SaveQueue
import sim.engine.data.Settings
import sim.engine.entity.World
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.npc.hunt.Hunting
import sim.engine.entity.character.player.Player
import sim.engine.entity.item.floor.FloorItemTracking
import sim.engine.entity.item.floor.FloorItems
import sim.engine.entity.obj.GameObjects
import sim.engine.event.AuditLog
import sim.engine.get
import sim.engine.map.zone.DynamicZones
import sim.engine.timer.toTicks
import sim.network.client.ConnectionQueue
import sim.network.login.protocol.npcVisualEncoders
import java.io.File
import java.util.concurrent.TimeUnit

val headlessTickStageOrder =
    listOf(
        "PlayerResetTask",
        "NPCResetTask",
        "NPCs",
        "InstructionTask",
        "World",
        "NPCTask",
        "PlayerTask",
        "FloorItemTracking",
        "GameObjects.timers",
        "DynamicZones",
        "ZoneBatchUpdates",
        "CharacterUpdateTask",
    )

fun describeTickStages(stages: List<Runnable>): List<String> = stages.map(::describeTickStage)

fun describeTickStage(stage: Runnable): String =
    when {
        stage is PlayerResetTask -> "PlayerResetTask"
        stage is NPCResetTask -> "NPCResetTask"
        stage is BotManager -> "BotManager"
        stage is Hunting -> "Hunting"
        stage is GrandExchange -> "GrandExchange"
        stage is ConnectionQueue -> "ConnectionQueue"
        stage === NPCs -> "NPCs"
        stage === FloorItems -> "FloorItems"
        stage is InstructionTask -> "InstructionTask"
        stage === World -> "World"
        stage is NPCTask -> "NPCTask"
        stage is PlayerTask -> "PlayerTask"
        stage is FloorItemTracking -> "FloorItemTracking"
        stage === GameObjects.timers -> "GameObjects.timers"
        stage is DynamicZones -> "DynamicZones"
        stage === ZoneBatchUpdates -> "ZoneBatchUpdates"
        stage is CharacterUpdateTask -> "CharacterUpdateTask"
        stage is SaveQueue -> "SaveQueue"
        stage is SaveLogs -> "SaveLogs"
        else -> stage::class.simpleName ?: stage::class.qualifiedName ?: "UnknownStage"
    }

fun getTickStages(
    floorItems: FloorItemTracking = get(),
    queue: ConnectionQueue = get(),
    accountSave: SaveQueue = get(),
    hunting: Hunting = get(),
    grandExchange: GrandExchange = get(),
    sequential: Boolean = CharacterTask.DEBUG,
    handlers: InstructionHandlers = get(),
    dynamicZones: DynamicZones = get(),
    botManager: BotManager = get(),
): List<Runnable> {
    val sequentialNpc: TaskIterator<NPC> = SequentialIterator()
    val sequentialPlayer: TaskIterator<Player> = SequentialIterator()
    val iterator: TaskIterator<Player> = if (sequential) SequentialIterator() else ParallelIterator()
    return listOf(
        PlayerResetTask(sequentialPlayer),
        NPCResetTask(sequentialNpc),
        botManager, // Bot must go after reset otherwise flags aren't seen when debugging bots
        hunting,
        grandExchange,
        // Connections/Tick Input
        queue,
        NPCs,
        FloorItems,
        // Tick
        InstructionTask(handlers),
        World,
        NPCTask(sequentialNpc),
        PlayerTask(sequentialPlayer),
        floorItems,
        GameObjects.timers,
        // Update
        dynamicZones,
        ZoneBatchUpdates,
        CharacterUpdateTask(
            iterator,
            PlayerUpdateTask(),
            NPCUpdateTask(npcVisualEncoders()),
        ),
        accountSave,
        SaveLogs(),
    )
}

fun getHeadlessTickStages(
    floorItems: FloorItemTracking = get(),
    handlers: InstructionHandlers = get(),
    dynamicZones: DynamicZones = get(),
    hunting: Hunting = get(),
    sequential: Boolean = CharacterTask.DEBUG,
): List<Runnable> {
    val sequentialNpc: TaskIterator<NPC> = SequentialIterator()
    val sequentialPlayer: TaskIterator<Player> = SequentialIterator()
    val iterator: TaskIterator<Player> = if (sequential) SequentialIterator() else ParallelIterator()
    return listOf(
        PlayerResetTask(sequentialPlayer),
        NPCResetTask(sequentialNpc),
        hunting, // Step 29: required for NPC aggression in Fight Caves
        NPCs,
        InstructionTask(handlers),
        World,
        NPCTask(sequentialNpc),
        PlayerTask(sequentialPlayer),
        floorItems,
        GameObjects.timers,
        dynamicZones,
        ZoneBatchUpdates,
        CharacterUpdateTask(
            iterator,
            PlayerUpdateTask(),
            NPCUpdateTask(npcVisualEncoders()),
        ),
    )
}

private class SaveLogs : Runnable {
    private val directory = File(Settings["storage.players.logs"])
    private var ticks = TimeUnit.SECONDS.toTicks(Settings["storage.players.logs.seconds", 10])
    private val logger = InlineLogger()

    init {
        directory.mkdirs()
    }

    override fun run() {
        if (ticks-- < 0) {
            if (AuditLog.logs.isEmpty) {
                return
            }
            val count = AuditLog.logs.size
            val start = System.currentTimeMillis()
            AuditLog.save(directory)
            logger.info { "Saved $count logs to disk in ${System.currentTimeMillis() - start}ms." }
            ticks = TimeUnit.SECONDS.toTicks(Settings["storage.players.logs.seconds", 10])
        }
    }
}



