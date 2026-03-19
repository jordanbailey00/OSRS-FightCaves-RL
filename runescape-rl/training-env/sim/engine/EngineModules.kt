package sim.engine

import org.koin.dsl.module
import org.rsmod.game.pathfinder.LineValidator
import org.rsmod.game.pathfinder.PathFinder
import org.rsmod.game.pathfinder.StepValidator
import sim.engine.client.PlayerAccountLoader
import sim.engine.client.update.batch.ZoneBatchUpdates
import sim.engine.data.*
import sim.engine.data.definition.*
import sim.engine.entity.character.npc.hunt.Hunting
import sim.engine.entity.character.player.equip.AppearanceOverrides
import sim.engine.entity.item.floor.FloorItemTracking
import sim.engine.entity.item.floor.FloorItems
import sim.engine.entity.obj.GameObjects
import sim.engine.map.collision.Collisions
import sim.engine.map.collision.GameObjectCollisionAdd
import sim.engine.map.collision.GameObjectCollisionRemove
import sim.engine.map.zone.DynamicZones
import sim.network.client.ConnectionQueue
import java.io.File

fun engineLoad(files: ConfigFiles) {
    Areas.load(files.list(Settings["map.areas"]))
    ZoneBatchUpdates.register(GameObjects)
    ZoneBatchUpdates.register(FloorItems)
}

fun engineModule(files: ConfigFiles) = module {
    val headless = Settings["runtime.mode", "headed"] == "headless"

    // Entities
    single { FloorItemTracking() }
    single { Hunting(get(), get()) }
    single {
        SaveQueue(get(), SafeStorage(File(Settings["storage.players.errors"])))
    }
    single { AccountManager(get(), get(), get(), get(), AppearanceOverrides()) }

    // IO
    single { PlayerAccountLoader(get(), get(), get(), get(), get(), Contexts.Game) }

    // Map
    single { DynamicZones(get()) }
    single(createdAtStart = true) { CanoeDefinitions().load(files.find(Settings["map.canoes"])) }

    // Network
    single {
        ConnectionQueue(Settings["network.maxLoginsPerTick", 1])
    }
    single(createdAtStart = true) { GameObjectCollisionAdd() }
    single(createdAtStart = true) { GameObjectCollisionRemove() }

    // Collision
    single { StepValidator(Collisions.map) }

    // Pathfinding
    single { PathFinder(flags = Collisions.map, useRouteBlockerFlags = true) }
    single { LineValidator(flags = Collisions.map) }

    // Definitions
    single(createdAtStart = true) { SoundDefinitions().load(files.list(Settings["definitions.sounds"])) }
    single(createdAtStart = true) { QuestDefinitions().load(files.find(Settings["definitions.quests"])) }
    single(createdAtStart = true) { RenderEmoteDefinitions().load(files.find(Settings["definitions.renderEmotes"])) }
    single(createdAtStart = true) { MidiDefinitions().load(files.list(Settings["definitions.midis"])) }
    single(createdAtStart = true) { JingleDefinitions().load(files.list(Settings["definitions.jingles"])) }
    single(createdAtStart = true) { SpellDefinitions().load(files.find(Settings["definitions.spells"])) }
    single(createdAtStart = true) { PatrolDefinitions().load(files.list(Settings["definitions.patrols"])) }
    single(createdAtStart = true) { PrayerDefinitions().load(files.find(Settings["definitions.prayers"])) }
    single(createdAtStart = true) { GearDefinitions().load(files.find(Settings["definitions.gearSets"])) }
    if (!headless) {
        single(createdAtStart = true) {
            get<ItemDefinitions>()
            DiangoCodeDefinitions().load(files.find(Settings["definitions.diangoCodes"]))
        }
    }
    single(createdAtStart = true) { AccountDefinitions().load() }
    single(createdAtStart = true) { HuntModeDefinitions().load(files.find(Settings["definitions.huntModes"])) }
    single(createdAtStart = true) { SlayerTaskDefinitions().load(files.list(Settings["definitions.slayerTasks"])) }
    single(createdAtStart = true) { CategoryDefinitions().load(files.find(Settings["definitions.categories"])) }
    single(createdAtStart = true) { ClientScriptDefinitions().load(files.list(Settings["definitions.clientScripts"])) }
    single(createdAtStart = true) { CombatDefinitions().load(files.list(Settings["definitions.combatAttacks"])) }
}