import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.RenderingHints
import java.awt.geom.Line2D
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

data class DemoLiteValidationOverlaySnapshot(
    val sessionText: String,
    val movementText: String,
    val playerText: String,
    val npcText: String,
    val eventConsoleText: String,
)

class DemoLiteValidationOverlay internal constructor(
    private val sessionIdProvider: () -> String,
    subscribeToEvents: (Boolean, (DemoLiteSessionEvent) -> Unit) -> Unit,
) {
    val panel = JPanel(BorderLayout(12, 12))

    private val sessionArea = textArea()
    private val movementArea = textArea()
    private val playerArea = textArea()
    private val npcArea = textArea()
    private val eventArea = textArea()
    private val worldPanel = DemoLiteWorldPanel()

    private val recentEvents = ArrayDeque<String>()
    private val npcLifecycle = linkedMapOf<Int, String>()
    private val npcMovementState = linkedMapOf<Int, String>()

    private var currentState: DemoLiteTickState? = null
    private var lastRuntimePhase: String = "booting"
    private var lastResetCause: String = "startup"
    private var lastTerminalReason: String? = null
    private var lastSessionEndReason: String? = null
    private var lastShutdownReason: String? = null
    private var lastStarterManifestPath: String? = null
    private var lastStarterStateSummary: String = "waiting for starter-state manifest"
    private var lastTileArrival: String? = null
    private var movementInterruptedReason: String? = null

    init {
        panel.border = BorderFactory.createTitledBorder("Temporary PR 7.1 Validation Overlay / Debug HUD")

        val left = JPanel(BorderLayout(8, 8))
        left.add(worldPanel, BorderLayout.CENTER)
        left.preferredSize = Dimension(520, 520)

        val right = JPanel(BorderLayout(8, 8))

        val topGrid = JPanel(GridLayout(2, 2, 8, 8))
        topGrid.add(scrollPanel("Session / Startup", sessionArea))
        topGrid.add(scrollPanel("Movement", movementArea))
        topGrid.add(scrollPanel("Player", playerArea))
        topGrid.add(scrollPanel("NPC / Wave", npcArea))

        right.add(topGrid, BorderLayout.CENTER)
        right.add(scrollPanel("Recent Events", eventArea), BorderLayout.SOUTH)

        panel.add(left, BorderLayout.CENTER)
        panel.add(right, BorderLayout.EAST)

        subscribeToEvents(true) { event ->
            recordEvent(event)
        }
        refreshPanels()
    }

    constructor(runtime: DemoLiteRuntime) : this(
        sessionIdProvider = runtime::sessionId,
        subscribeToEvents = { replayExisting, listener ->
            runtime.addSessionEventListener(replayExisting = replayExisting, listener = listener)
        },
    )

    fun renderState(state: DemoLiteTickState) {
        val previous = currentState
        currentState = state
        updateNpcMovement(previous, state)
        if (state.movement.movementState == "arrived") {
            lastTileArrival = formatTile(state.movement.currentTile)
        }
        worldPanel.render(state = state, npcLifecycle = npcLifecycle)
        refreshPanels()
    }

    fun recordEvent(event: DemoLiteSessionEvent) {
        updateOverlayMetadata(event)
        DemoLiteOverlayEventFormatter.describe(event)?.let(::appendEventLine)
        refreshPanels()
    }

    fun snapshotForTest(): DemoLiteValidationOverlaySnapshot =
        DemoLiteValidationOverlaySnapshot(
            sessionText = sessionArea.text,
            movementText = movementArea.text,
            playerText = playerArea.text,
            npcText = npcArea.text,
            eventConsoleText = eventArea.text,
        )

    private fun refreshPanels() {
        sessionArea.text = buildSessionPanelText()
        movementArea.text = buildMovementPanelText()
        playerArea.text = buildPlayerPanelText()
        npcArea.text = buildNpcPanelText()
        eventArea.text = recentEvents.joinToString(separator = "\n")
    }

    private fun buildSessionPanelText(): String {
        val state = currentState
        return buildString {
            appendLine("Session: ${sessionIdProvider()}")
            appendLine("Episode: ${episodeId(state)}")
            appendLine("Tick: ${state?.observation?.tick ?: "?"}")
            appendLine("Runtime phase: $lastRuntimePhase")
            appendLine("Reset state: $lastResetCause")
            appendLine("Terminal: ${state?.terminalCode?.name ?: lastTerminalReason ?: "NONE"}")
            appendLine("Session/shutdown reason: ${lastSessionEndReason ?: lastShutdownReason ?: "running"}")
            appendLine("Starter contract: ${if (state != null) "loaded" else "booting"}")
            appendLine("Start wave: ${state?.observation?.wave?.wave ?: "?"}")
            appendLine("Run ON: ${state?.observation?.player?.running ?: "?"}")
            appendLine("Ammo: ${state?.observation?.player?.consumables?.ammoCount ?: "?"}")
            appendLine("Sharks: ${state?.observation?.player?.consumables?.sharkCount ?: "?"}")
            appendLine("Prayer doses: ${state?.observation?.player?.consumables?.prayerPotionDoseCount ?: "?"}")
            appendLine("Spawn/current tile: ${state?.observation?.player?.tile?.let(::formatTile) ?: "?"}")
            appendLine("Starter manifest: ${lastStarterManifestPath ?: "pending"}")
            appendLine("Starter summary: $lastStarterStateSummary")
        }
    }

    private fun buildMovementPanelText(): String {
        val movement = currentState?.movement
        return buildString {
            appendLine("current_tile: ${movement?.currentTile?.let(::formatTile) ?: "?"}")
            appendLine("destination_tile: ${movement?.destinationTile?.let(::formatTile) ?: "null"}")
            appendLine("movement_state: ${movement?.movementState ?: "?"}")
            appendLine("movement_source: ${movement?.movementSource ?: "?"}")
            appendLine("movement_source_detail: ${movement?.movementSourceDetail ?: "n/a"}")
            appendLine("path_remaining_steps: ${approxPathRemainingSteps(movement)}")
            appendLine("movement_interrupted_reason: ${movementInterruptedReason ?: "n/a"}")
            appendLine("last_tile_arrival: ${lastTileArrival ?: "n/a"}")
            appendLine("mode: ${movement?.mode ?: "?"}")
        }
    }

    private fun buildPlayerPanelText(): String {
        val state = currentState
        val player = state?.observation?.player
        val result = state?.lastActionResult
        val selectedTarget = selectedTargetLabel(state)
        return buildString {
            appendLine("HP / Constitution: ${player?.hitpointsCurrent ?: "?"}/${player?.hitpointsMax ?: "?"}")
            appendLine("Prayer: ${player?.prayerCurrent ?: "?"}/${player?.prayerMax ?: "?"}")
            appendLine("Run energy: ${player?.runEnergyPercent ?: "?"}%")
            appendLine("Run toggle: ${if (player?.running == true) "ON" else if (player?.running == false) "OFF" else "?"}")
            appendLine("Ammo: ${player?.consumables?.ammoCount ?: "?"}")
            appendLine("Sharks: ${player?.consumables?.sharkCount ?: "?"}")
            appendLine("Prayer doses: ${player?.consumables?.prayerPotionDoseCount ?: "?"}")
            appendLine("Protection prayers: ${protectionPrayerSummary(player)}")
            appendLine("Current target: $selectedTarget")
            appendLine("Current action state: ${result?.actionType?.name ?: "reset"}")
            appendLine("Action applied: ${result?.actionApplied ?: "n/a"}")
            appendLine("Action rejection: ${result?.rejectionReason?.name ?: "none"}")
            appendLine("Attack resolution: ${result?.metadata?.get("attack_resolution") ?: "n/a"}")
            appendLine("Action destination: ${result?.metadata?.get("destination_tile") ?: "n/a"}")
            appendLine("Wave: ${state?.observation?.wave?.wave ?: "?"}")
            appendLine("Session / episode / tick: ${sessionIdProvider()} / ${episodeId(state)} / ${state?.observation?.tick ?: "?"}")
            appendLine("Runtime phase: $lastRuntimePhase")
            appendLine("Reset state: $lastResetCause")
            appendLine("Terminal state: ${state?.terminalCode?.name ?: lastTerminalReason ?: "NONE"}")
            appendLine("Session/shutdown reason: ${lastSessionEndReason ?: lastShutdownReason ?: "running"}")
        }
    }

    private fun buildNpcPanelText(): String {
        val state = currentState
        val tracked = state?.trackedNpcs ?: emptyList()
        val aliveCount = tracked.count { !it.dead && !it.hidden }
        val visibleCount = state?.visibleTargets?.size ?: 0
        return buildString {
            appendLine("Current wave: ${state?.observation?.wave?.wave ?: "?"}")
            appendLine("Alive NPC count: $aliveCount")
            appendLine("Visible NPC count: $visibleCount")
            appendLine("Tracked NPC count: ${tracked.size}")
            appendLine("Selected target: ${selectedTargetLabel(state)}")
            appendLine()
            if (tracked.isEmpty()) {
                appendLine("No tracked NPCs.")
            } else {
                tracked.forEach { npc ->
                    val lifecycle = npcLifecycle[npc.npcIndex] ?: if (npc.visibleToPlayer) "npc_newly_visible" else "tracked"
                    val motion = npcMovementState[npc.npcIndex] ?: "steady"
                    appendLine(
                        "[${npc.npcIndex}] ${npc.id} tile=${formatTile(npc.tile)} alive=${!npc.dead} visible=${npc.visibleToPlayer} tracked=true movement=$motion lifecycle=$lifecycle"
                    )
                }
            }
        }
    }

    private fun updateOverlayMetadata(event: DemoLiteSessionEvent) {
        when (event.eventType) {
            "starter_state_manifest_written" -> {
                lastStarterManifestPath = event.payload["path"] as? String
                val manifest = event.payload["manifest"] as? Map<*, *>
                if (manifest != null) {
                    lastStarterStateSummary =
                        buildString {
                            append("wave=${manifest["observed_wave"]}")
                            append(", ammo=${manifest["ammo_count"]}")
                            append(", sharks=${manifest["shark_count"]}")
                            append(", doses=${manifest["prayer_potion_dose_count"]}")
                            append(", running=${manifest["running"]}")
                            append(", tile=${formatTileMap(manifest["observed_player_tile"])}")
                        }
                }
            }
            "reset_begin" -> lastResetCause = event.payload["cause"] as? String ?: "reset"
            "reset_complete" -> {
                lastResetCause = event.payload["cause"] as? String ?: "reset_complete"
                lastTerminalReason = event.payload["terminal_code"] as? String
            }
            "terminal_emitted" -> lastTerminalReason = event.payload["terminal_code"] as? String
            "session_end" -> lastSessionEndReason = event.payload["session_end_reason"] as? String
            "combat_loop_stop" -> lastShutdownReason = event.payload["reason"] as? String
            "tick_state", "action_handled", "reset_decision", "terminal_emitted" -> {
                val phase = event.payload["runtime_phase"] as? String
                if (phase != null) {
                    lastRuntimePhase = phase
                }
            }
            "state_delta" -> handleStateDelta(event)
        }
    }

    private fun handleStateDelta(event: DemoLiteSessionEvent) {
        val changes = event.payload["changes"] as? List<*> ?: return
        changes.forEach { change ->
            val map = change as? Map<*, *> ?: return@forEach
            when (map["field"] as? String) {
                "movement_arrived" -> {
                    val to = map["to"] as? Map<*, *>
                    lastTileArrival = formatTileMap(to?.get("current_tile"))
                    movementInterruptedReason = null
                }
                "movement_interrupted" -> {
                    movementInterruptedReason = "movement_interrupted"
                }
                "npc_tracking_added" -> updateNpcLifecycle(map["to"], fallbackLabel = "npc_tracking_added")
                "npc_newly_visible" -> updateNpcLifecycle(map["to"], fallbackLabel = "npc_newly_visible")
                "npc_no_longer_visible" -> updateNpcLifecycle(map["from"], fallbackLabel = "npc_no_longer_visible")
                "npc_tracking_removed" -> updateNpcLifecycle(map["from"], fallbackLabel = "npc_tracking_removed")
                "npc_defeated" -> updateNpcLifecycle(map["to"], fallbackLabel = "npc_defeated")
            }
        }
    }

    private fun updateNpcLifecycle(payload: Any?, fallbackLabel: String) {
        val entries = payload as? List<*> ?: return
        entries.forEach { entry ->
            val map = entry as? Map<*, *>
            if (map != null && map["npc"] is Map<*, *>) {
                val npc = map["npc"] as Map<*, *>
                val npcIndex = (npc["npc_index"] as? Number)?.toInt() ?: return@forEach
                val label = map["classification"] as? String ?: fallbackLabel
                npcLifecycle[npcIndex] = displayLifecycleLabel(label)
            } else if (entry is Map<*, *>) {
                val npcIndex = (entry["npc_index"] as? Number)?.toInt() ?: return@forEach
                npcLifecycle[npcIndex] = displayLifecycleLabel(fallbackLabel)
            }
        }
    }

    private fun updateNpcMovement(previous: DemoLiteTickState?, current: DemoLiteTickState) {
        val previousByNpc = previous?.trackedNpcs?.associateBy { it.npcIndex } ?: emptyMap()
        npcMovementState.clear()
        current.trackedNpcs.forEach { npc ->
            val previousTile = previousByNpc[npc.npcIndex]?.tile
            npcMovementState[npc.npcIndex] =
                when {
                    previousTile == null -> "new"
                    previousTile != npc.tile -> "moving"
                    else -> "steady"
                }
        }
    }

    private fun appendEventLine(line: String) {
        recentEvents.addFirst(line)
        while (recentEvents.size > 18) {
            recentEvents.removeLast()
        }
    }

    private fun textArea(): JTextArea =
        JTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
        }

    private fun scrollPanel(title: String, area: JTextArea): JScrollPane =
        JScrollPane(area).apply {
            preferredSize = Dimension(360, 180)
            border = BorderFactory.createTitledBorder(title)
        }

    private fun episodeId(state: DemoLiteTickState?): String =
        state?.let { "episode-${it.resetCount.toString().padStart(4, '0')}" } ?: "pending"

    private fun selectedTargetLabel(state: DemoLiteTickState?): String {
        val action = state?.lastAction as? HeadlessAction.AttackVisibleNpc ?: return "none"
        val target = state.visibleTargets.getOrNull(action.visibleNpcIndex) ?: return "visible_index=${action.visibleNpcIndex}"
        return "[${target.visibleIndex}] ${target.id} npc=${target.npcIndex}"
    }

    private fun protectionPrayerSummary(player: HeadlessObservationPlayer?): String {
        if (player == null) {
            return "?"
        }
        return buildList {
            if (player.protectionPrayers.protectFromMagic) add("magic")
            if (player.protectionPrayers.protectFromMissiles) add("missiles")
            if (player.protectionPrayers.protectFromMelee) add("melee")
        }.ifEmpty { listOf("none") }.joinToString(", ")
    }

    private fun approxPathRemainingSteps(movement: DemoLiteMovementSnapshot?): String {
        if (movement?.destinationTile == null) {
            return "n/a"
        }
        val dx = kotlin.math.abs(movement.currentTile.x - movement.destinationTile.x)
        val dy = kotlin.math.abs(movement.currentTile.y - movement.destinationTile.y)
        return maxOf(dx, dy).toString()
    }

    private fun displayLifecycleLabel(label: String): String =
        when (label) {
            "wave_start_world_spawn" -> "wave_spawn"
            "newly_visible" -> "npc_newly_visible"
            "no_longer_visible" -> "npc_no_longer_visible"
            "despawned" -> "npc_tracking_removed"
            else -> label
        }

    private fun formatTile(tile: HeadlessObservationTile): String = "(${tile.x}, ${tile.y}, ${tile.level})"

    private fun formatTileMap(tile: Any?): String =
        (tile as? Map<*, *>)?.let { map ->
            val x = map["x"] ?: "?"
            val y = map["y"] ?: "?"
            val level = map["level"] ?: "?"
            "($x, $y, $level)"
        } ?: "n/a"
}

private object DemoLiteOverlayEventFormatter {
    fun describe(event: DemoLiteSessionEvent): String? {
        val prefix = buildString {
            append("tick ")
            append(event.tick ?: "-")
            append(" | ")
        }
        return when (event.eventType) {
            "session_start" -> "$prefix session started: ${event.sessionId}"
            "runtime_initialized" -> "$prefix runtime initialized"
            "combat_loop_start" -> "$prefix combat loop started"
            "startup_movement_intent" ->
                "$prefix startup movement attributed to shared reset_spawn -> ${formatTileMap(event.payload["destination_tile"])}"
            "input_requested" -> {
                val request = event.payload["request"] as? Map<*, *> ?: return null
                "$prefix requested ${request["label"]} from ${request["source"]}"
            }
            "action_handled" -> describeActionHandled(prefix, event.payload)
            "input_discarded" -> {
                val request = event.payload["request"] as? Map<*, *>
                val label = request?.get("label") ?: "input"
                val reason = event.payload["discard_reason"] ?: "discarded"
                "$prefix $label $reason"
            }
            "reset_begin" -> "$prefix reset begin: ${event.payload["cause"]}"
            "reset_complete" -> "$prefix reset complete: cause=${event.payload["cause"]} wave=${event.payload["wave"]}"
            "reset_decision" -> "$prefix reset decision: now=${event.payload["reset_now"]} reason=${event.payload["reason"]}"
            "terminal_emitted" -> "$prefix terminal: ${event.payload["terminal_code"]}"
            "combat_loop_stop" -> "$prefix runtime stop: ${event.payload["reason"]}"
            "session_end" -> "$prefix session end: ${event.payload["session_end_reason"]}"
            "state_delta" -> describeStateDelta(prefix, event.payload)
            else -> null
        }
    }

    private fun describeActionHandled(prefix: String, payload: Map<String, Any?>): String {
        val result = payload["action_result"] as? Map<*, *>
        val label = payload["label"] ?: payload["source"] ?: "action"
        val applied = result?.get("action_applied")
        val rejection = result?.get("rejection_reason")
        val metadata = result?.get("metadata") as? Map<*, *>
        val attackResolution = metadata?.get("attack_resolution")
        val deferred = payload["deferred"]
        return when {
            applied == false -> "$prefix $label rejected: ${rejection ?: "unknown"}"
            attackResolution != null -> "$prefix $label accepted${if (deferred == true) " (deferred)" else ""}: $attackResolution"
            else -> "$prefix $label accepted${if (deferred == true) " (deferred)" else ""}"
        }
    }

    private fun describeStateDelta(prefix: String, payload: Map<String, Any?>): String? {
        val changes = payload["changes"] as? List<*> ?: return null
        val lines =
            changes.mapNotNull { change ->
                val map = change as? Map<*, *> ?: return@mapNotNull null
                when (map["field"] as? String) {
                    "player_tile" -> "$prefix moved to ${formatTileMap(map["to"])}"
                    "movement_destination_tile" -> "$prefix destination -> ${formatTileMap(map["to"])}"
                    "movement_arrived" -> "$prefix movement arrived"
                    "movement_interrupted" -> "$prefix movement interrupted"
                    "wave" -> "$prefix wave advanced: ${map["from"]} -> ${map["to"]}"
                    "npc_tracking_added" -> "$prefix ${describeNpcLifecycle(map["to"], fallback = "npc_tracking_added")}"
                    "npc_newly_visible" -> "$prefix ${describeNpcLifecycle(map["to"], fallback = "npc_newly_visible")}"
                    "npc_no_longer_visible" -> "$prefix ${describeNpcLifecycle(map["from"], fallback = "npc_no_longer_visible")}"
                    "npc_tracking_removed" -> "$prefix ${describeNpcLifecycle(map["from"], fallback = "npc_tracking_removed")}"
                    "npc_defeated" -> "$prefix ${describeNpcLifecycle(map["to"], fallback = "npc_defeated")}"
                    "terminal_code" -> "$prefix terminal changed: ${map["to"]}"
                    else -> null
                }
            }
        return lines.firstOrNull()
    }

    private fun describeNpcLifecycle(payload: Any?, fallback: String): String {
        val first = (payload as? List<*>)?.firstOrNull() as? Map<*, *> ?: return fallback
        val npc = (first["npc"] as? Map<*, *>) ?: first
        val npcId = npc["id"] ?: "npc"
        val npcIndex = npc["npc_index"] ?: "?"
        val classification = first["classification"] ?: fallback
        return "${displayLifecycleLabel(classification.toString())}: $npcId#$npcIndex"
    }

    private fun displayLifecycleLabel(label: String): String =
        when (label) {
            "wave_start_world_spawn" -> "wave_spawn"
            "newly_visible" -> "npc_newly_visible"
            "no_longer_visible" -> "npc_no_longer_visible"
            "despawned" -> "npc_tracking_removed"
            else -> label
        }

    private fun formatTileMap(tile: Any?): String =
        (tile as? Map<*, *>)?.let { map ->
            val x = map["x"] ?: "?"
            val y = map["y"] ?: "?"
            val level = map["level"] ?: "?"
            "($x, $y, $level)"
        } ?: "n/a"
}

private class DemoLiteWorldPanel : JPanel() {
    private var state: DemoLiteTickState? = null
    private var npcLifecycle: Map<Int, String> = emptyMap()

    init {
        preferredSize = Dimension(520, 520)
        minimumSize = Dimension(360, 360)
        background = Color(0x12, 0x12, 0x12)
        border = BorderFactory.createTitledBorder("Simple Tile Visualizer")
    }

    fun render(
        state: DemoLiteTickState,
        npcLifecycle: Map<Int, String>,
    ) {
        this.state = state
        this.npcLifecycle = LinkedHashMap(npcLifecycle)
        repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val state = state ?: return
        val g = graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val playerTile = state.observation.player.tile
        val destination = state.movement.destinationTile
        val tracked = state.trackedNpcs
        val allTiles = buildList {
            add(playerTile)
            if (destination != null) {
                add(destination)
            }
            tracked.forEach { add(it.tile) }
        }
        val minX = (allTiles.minOf { it.x } - 2)
        val maxX = (allTiles.maxOf { it.x } + 2)
        val minY = (allTiles.minOf { it.y } - 2)
        val maxY = (allTiles.maxOf { it.y } + 2)
        val gridWidth = (maxX - minX + 1).coerceAtLeast(1)
        val gridHeight = (maxY - minY + 1).coerceAtLeast(1)
        val cell = minOf((width - 40) / gridWidth, (height - 60) / gridHeight).coerceAtLeast(18)
        val originX = 20
        val originY = 30

        g.color = Color(0x22, 0x22, 0x22)
        g.fillRect(originX, originY, gridWidth * cell, gridHeight * cell)
        g.color = Color(0x33, 0x33, 0x33)
        for (x in 0..gridWidth) {
            val drawX = originX + x * cell
            g.drawLine(drawX, originY, drawX, originY + gridHeight * cell)
        }
        for (y in 0..gridHeight) {
            val drawY = originY + y * cell
            g.drawLine(originX, drawY, originX + gridWidth * cell, drawY)
        }

        if (destination != null) {
            val from = pointForTile(playerTile, minX, maxY, originX, originY, cell)
            val to = pointForTile(destination, minX, maxY, originX, originY, cell)
            g.color = Color(0x58, 0xd0, 0xff)
            g.stroke = BasicStroke(2f)
            g.draw(Line2D.Float(from.first, from.second, to.first, to.second))
        }

        tracked.forEach { npc ->
            val (drawX, drawY) = pointForTile(npc.tile, minX, maxY, originX, originY, cell)
            g.color =
                when {
                    npc.dead -> Color(0x66, 0x66, 0x66)
                    npc.visibleToPlayer -> Color(0xff, 0xb0, 0x3b)
                    else -> Color(0x9c, 0x6d, 0x2b)
                }
            g.fillRect((drawX - cell * 0.3f).toInt(), (drawY - cell * 0.3f).toInt(), (cell * 0.6f).toInt(), (cell * 0.6f).toInt())
            g.color = Color.WHITE
            g.font = Font(Font.MONOSPACED, Font.PLAIN, 10)
            g.drawString("${npc.id}#${npc.npcIndex}", drawX - cell / 2f, drawY - cell / 2f - 4f)
            npcLifecycle[npc.npcIndex]?.let { lifecycle ->
                g.drawString(lifecycle, drawX - cell / 2f, drawY + cell / 2f + 12f)
            }
        }

        val (playerX, playerY) = pointForTile(playerTile, minX, maxY, originX, originY, cell)
        g.color = Color(0x45, 0x9c, 0xff)
        g.fillOval((playerX - cell * 0.35f).toInt(), (playerY - cell * 0.35f).toInt(), (cell * 0.7f).toInt(), (cell * 0.7f).toInt())
        g.color = Color.WHITE
        g.font = Font(Font.MONOSPACED, Font.BOLD, 11)
        g.drawString("PLAYER", playerX - cell / 2f, playerY - cell / 2f - 4f)

        if (destination != null) {
            val (destX, destY) = pointForTile(destination, minX, maxY, originX, originY, cell)
            g.color = Color(0x58, 0xd0, 0xff)
            g.stroke = BasicStroke(2f)
            g.drawRect((destX - cell * 0.4f).toInt(), (destY - cell * 0.4f).toInt(), (cell * 0.8f).toInt(), (cell * 0.8f).toInt())
            g.drawString("DEST", destX - cell / 2f, destY + cell / 2f + 24f)
        }

        val selectedTarget = (state.lastAction as? HeadlessAction.AttackVisibleNpc)?.visibleNpcIndex
        if (selectedTarget != null) {
            state.visibleTargets.firstOrNull { it.visibleIndex == selectedTarget }?.let { target ->
                val (targetX, targetY) = pointForTile(HeadlessObservationTile.from(target.tile), minX, maxY, originX, originY, cell)
                g.color = Color.RED
                g.stroke = BasicStroke(2f)
                g.drawOval((targetX - cell * 0.5f).toInt(), (targetY - cell * 0.5f).toInt(), cell, cell)
            }
        }
    }

    private fun pointForTile(
        tile: HeadlessObservationTile,
        minX: Int,
        maxY: Int,
        originX: Int,
        originY: Int,
        cell: Int,
    ): Pair<Float, Float> {
        val gridX = tile.x - minX
        val gridY = maxY - tile.y
        val drawX = originX + gridX * cell + cell / 2f
        val drawY = originY + gridY * cell + cell / 2f
        return drawX to drawY
    }
}
