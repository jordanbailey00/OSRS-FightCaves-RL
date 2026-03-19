import world.gregs.voidps.type.Tile
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

class DemoLiteUi(
    private val runtime: DemoLiteRuntime,
) {
    private val frame = JFrame("Fight Caves Demo Lite")
    private val statusLabel = JLabel("Booting demo-lite runtime...")
    private val waveLabel = JLabel("")
    private val terminalLabel = JLabel("")
    private val actionLabel = JLabel("")
    private val resetLabel = JLabel("")
    private val validationOverlay = DemoLiteValidationOverlay(runtime)
    private val targetPanel = JPanel()
    private var currentState: DemoLiteTickState? = null

    fun showWindow() {
        val root = JPanel(BorderLayout(12, 12))
        root.border = BorderFactory.createEmptyBorder(12, 12, 12, 12)

        val heading = JLabel(loadBanner())
        heading.font = Font(Font.MONOSPACED, Font.BOLD, 18)

        val header = JPanel()
        header.layout = BoxLayout(header, BoxLayout.Y_AXIS)
        header.add(heading)
        header.add(statusLabel)
        header.add(waveLabel)
        header.add(terminalLabel)
        header.add(actionLabel)
        header.add(resetLabel)

        val controls = buildControls()

        root.add(header, BorderLayout.NORTH)
        root.add(validationOverlay.panel, BorderLayout.CENTER)
        root.add(controls, BorderLayout.SOUTH)

        frame.contentPane = root
        frame.setSize(1_520, 960)
        frame.setLocationRelativeTo(null)
        frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        frame.addWindowListener(
            object : WindowAdapter() {
                override fun windowClosed(event: WindowEvent) {
                    runtime.close(reason = "user_window_close")
                }
            },
        )
        bindKeys()
        runtime.start { state ->
            SwingUtilities.invokeLater {
                render(state)
            }
        }
        frame.isVisible = true
    }

    private fun buildControls(): JPanel {
        val controls = JPanel(BorderLayout(12, 12))

        val actions = JPanel(GridLayout(2, 4, 8, 8))
        actions.border = BorderFactory.createTitledBorder("Actions")
        actions.add(button("Wait") { runtime.queueAction(HeadlessAction.Wait, source = "ui.button.wait") })
        actions.add(button("Eat Shark") { runtime.queueAction(HeadlessAction.EatShark, source = "ui.button.eat_shark") })
        actions.add(button("Drink Prayer") { runtime.queueAction(HeadlessAction.DrinkPrayerPotion, source = "ui.button.drink_prayer") })
        actions.add(button("Toggle Run") { runtime.queueAction(HeadlessAction.ToggleRun, source = "ui.button.toggle_run") })
        actions.add(button("Protect Magic") { queuePrayer(HeadlessProtectionPrayer.ProtectFromMagic, source = "ui.button.protect_magic") })
        actions.add(button("Protect Missiles") { queuePrayer(HeadlessProtectionPrayer.ProtectFromMissiles, source = "ui.button.protect_missiles") })
        actions.add(button("Protect Melee") { queuePrayer(HeadlessProtectionPrayer.ProtectFromMelee, source = "ui.button.protect_melee") })
        actions.add(button("Restart") { runtime.requestReset(source = "ui.button.restart") })

        val movement = JPanel(GridLayout(2, 3, 8, 8))
        movement.border = BorderFactory.createTitledBorder("Movement")
        movement.add(JPanel())
        movement.add(button("North") { queueWalk(0, 1, source = "ui.button.walk_north") })
        movement.add(JPanel())
        movement.add(button("West") { queueWalk(-1, 0, source = "ui.button.walk_west") })
        movement.add(button("South") { queueWalk(0, -1, source = "ui.button.walk_south") })
        movement.add(button("East") { queueWalk(1, 0, source = "ui.button.walk_east") })

        targetPanel.layout = BoxLayout(targetPanel, BoxLayout.Y_AXIS)
        val targetScroll = JScrollPane(targetPanel)
        targetScroll.border = BorderFactory.createTitledBorder("Attack Visible Targets")
        targetScroll.preferredSize = java.awt.Dimension(340, 120)

        controls.add(targetScroll, BorderLayout.WEST)
        controls.add(actions, BorderLayout.CENTER)
        controls.add(movement, BorderLayout.EAST)
        return controls
    }

    private fun bindKeys() {
        bindKey("SPACE", "wait") { runtime.queueAction(HeadlessAction.Wait, source = "ui.key.space") }
        bindKey("R", "restart") { runtime.requestReset(source = "ui.key.r") }
        bindKey("E", "eat-shark") { runtime.queueAction(HeadlessAction.EatShark, source = "ui.key.e") }
        bindKey("P", "drink-prayer") { runtime.queueAction(HeadlessAction.DrinkPrayerPotion, source = "ui.key.p") }
        bindKey("Q", "toggle-run") { runtime.queueAction(HeadlessAction.ToggleRun, source = "ui.key.q") }
        bindKey("M", "protect-magic") { queuePrayer(HeadlessProtectionPrayer.ProtectFromMagic, source = "ui.key.m") }
        bindKey("N", "protect-missiles") { queuePrayer(HeadlessProtectionPrayer.ProtectFromMissiles, source = "ui.key.n") }
        bindKey("B", "protect-melee") { queuePrayer(HeadlessProtectionPrayer.ProtectFromMelee, source = "ui.key.b") }
        bindKey("UP", "walk-north") { queueWalk(0, 1, source = "ui.key.up") }
        bindKey("DOWN", "walk-south") { queueWalk(0, -1, source = "ui.key.down") }
        bindKey("LEFT", "walk-west") { queueWalk(-1, 0, source = "ui.key.left") }
        bindKey("RIGHT", "walk-east") { queueWalk(1, 0, source = "ui.key.right") }
        (1..9).forEach { index ->
            bindKey(index.toString(), "attack-target-$index") {
                runtime.queueAction(
                    HeadlessAction.AttackVisibleNpc(index - 1),
                    source = "ui.key.$index",
                    payload = mapOf("visible_target_index" to (index - 1)),
                )
            }
        }
    }

    private fun bindKey(key: String, id: String, action: () -> Unit) {
        val rootPane = frame.rootPane
        rootPane.inputMap.put(KeyStroke.getKeyStroke(key), id)
        rootPane.actionMap.put(
            id,
            object : javax.swing.AbstractAction() {
                override fun actionPerformed(event: java.awt.event.ActionEvent?) {
                    action()
                }
            },
        )
    }

    private fun queuePrayer(prayer: HeadlessProtectionPrayer, source: String) {
        runtime.queueAction(
            HeadlessAction.ToggleProtectionPrayer(prayer),
            source = source,
            payload = mapOf("prayer" to prayer.prayerId),
        )
    }

    private fun queueWalk(deltaX: Int, deltaY: Int, source: String) {
        val state = currentState ?: return
        val tile = state.observation.player.tile
        val nextTile =
            Tile(
                tile.x + deltaX,
                tile.y + deltaY,
                tile.level,
            )
        runtime.queueAction(
            HeadlessAction.WalkToTile(nextTile),
            source = source,
            payload = mapOf("target_tile" to "${nextTile.x},${nextTile.y},${nextTile.level}"),
        )
    }

    private fun render(state: DemoLiteTickState) {
        currentState = state
        val observation = state.observation
        statusLabel.text =
            "Tick ${observation.tick} | HP ${observation.player.hitpointsCurrent}/${observation.player.hitpointsMax} | " +
                "Prayer ${observation.player.prayerCurrent}/${observation.player.prayerMax} | " +
                "Run ${if (observation.player.running) "on" else "off"} (${observation.player.runEnergyPercent}%)"
        waveLabel.text = DemoLiteWaveLogic.describeWave(observation)
        terminalLabel.text =
            "Terminal ${state.terminalCode.code}: ${state.terminalCode.name.lowercase()} | " +
                "Jad telegraph ${DemoLiteJadTelegraph.describe(observation)}"
        actionLabel.text =
            state.lastActionResult?.let {
                val rejection = it.rejectionReason?.name ?: "none"
                "Last action: ${it.actionType.name} | applied=${it.actionApplied} | rejection=$rejection | attack_resolution=${it.metadata["attack_resolution"] ?: "n/a"}"
            } ?: "Last action: reset"
        resetLabel.text =
            buildString {
                append("Episode ${state.resetCount} | Seed ${state.sessionSeed}")
                if (state.autoResetRemainingMillis != null) {
                    append(" | auto-reset in ")
                    append(String.format("%.1fs", state.autoResetRemainingMillis / 1000.0))
                }
            }
        renderTargets(state)
        validationOverlay.renderState(state)
    }

    private fun renderTargets(state: DemoLiteTickState) {
        targetPanel.removeAll()
        if (state.visibleTargets.isEmpty()) {
            targetPanel.add(JLabel("No visible targets."))
        } else {
            state.visibleTargets.forEach { target ->
                val npc = state.observation.npcs.firstOrNull { it.visibleIndex == target.visibleIndex }
                targetPanel.add(
                    button(
                        "[${target.visibleIndex + 1}] ${target.id} hp=${npc?.hitpointsCurrent ?: "?"}/${npc?.hitpointsMax ?: "?"} tile=${target.tile.x},${target.tile.y}"
                    ) {
                        runtime.queueAction(
                            HeadlessAction.AttackVisibleNpc(target.visibleIndex),
                            source = "ui.button.attack_visible_target",
                            payload = mapOf("visible_target_index" to target.visibleIndex, "target_id" to target.id),
                        )
                    }
                )
            }
        }
        targetPanel.revalidate()
        targetPanel.repaint()
    }

    private fun button(label: String, onClick: () -> Unit): JButton =
        JButton(label).apply {
            addActionListener { onClick() }
        }

    private fun loadBanner(): String =
        javaClass.getResourceAsStream("/demo-lite-banner.txt")
            ?.bufferedReader()
            ?.use { it.readText().trim() }
            ?.takeIf { it.isNotBlank() }
            ?: "Fight Caves Demo Lite"
}
