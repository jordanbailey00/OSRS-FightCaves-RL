import org.koin.core.context.stopKoin
import sim.engine.GameLoop
import sim.engine.Script
import sim.engine.data.Settings
import sim.engine.entity.World
import sim.engine.entity.character.npc.NPCs
import sim.engine.entity.character.player.Players
import sim.engine.entity.item.floor.FloorItems
import sim.engine.entity.obj.GameObjects
import sim.engine.map.collision.Collisions
import sim.engine.map.instance.Instances

internal fun headlessTestOverrides(): Map<String, String> =
    mapOf(
        "storage.data" to "../data/",
        "storage.data.modified" to "../temp/data/headless-test-cache/modified.dat",
        "storage.cache.path" to "../data/cache/",
        "storage.wildcards" to "../temp/data/headless-test-cache/wildcards.txt",
        "storage.caching.path" to "../temp/data/headless-test-cache/",
        "storage.caching.active" to "false",
        "storage.players.path" to "../temp/data/test-saves/",
        "storage.players.logs" to "../temp/data/test-logs/",
        "storage.players.errors" to "../temp/data/test-errors/",
        "storage.autoSave.minutes" to "0",
        "events.shootingStars.enabled" to "false",
        "events.penguinHideAndSeek.enabled" to "false",
        "bots.count" to "0",
        "world.npcs.randomWalk" to "false",
        "storage.disabled" to "true",
        "headless.data.allowlist.path" to "config/headless_data_allowlist.toml",
        "headless.scripts.allowlist.path" to "config/headless_scripts.txt",
        "headless.manifest.path" to "config/headless_manifest.toml",
    )

internal fun resetHeadlessTestRuntime() {
    Players.clear()
    NPCs.clear()
    FloorItems.clear()
    GameObjects.reset()
    World.clear()
    Instances.reset()
    Collisions.clear()
    Script.clear()
    GameLoop.tick = 0
    Settings.clear()
    stopKoin()
}

