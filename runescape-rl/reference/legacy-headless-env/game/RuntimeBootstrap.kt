import com.github.michaelbull.logging.InlineLogger
import content.entity.obj.ObjectTeleports
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.logger.Level
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.logger.slf4jLogger
import sim.cache.Cache
import sim.cache.Index
import sim.cache.config.decoder.InventoryDecoder
import sim.cache.config.decoder.StructDecoder
import sim.cache.definition.decoder.*
import sim.cache.secure.Huffman
import sim.engine.*
import sim.engine.data.*
import sim.engine.data.definition.*
import sim.engine.entity.Despawn
import sim.engine.entity.item.drop.DropTables
import sim.engine.event.AuditLog
import sim.engine.event.Wildcards
import sim.engine.map.collision.CollisionDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.concurrent.thread

const val RUNTIME_MODE_KEY = "runtime.mode"

enum class RuntimeMode(val id: String) {
    Headed("headed"),
    Headless("headless"),
}

fun currentRuntimeMode(): String = Settings[RUNTIME_MODE_KEY, RuntimeMode.Headed.id]

private val headlessPrunedDefaults: Map<String, String> =
    mapOf(
        "bots.count" to "0",
        "events.shootingStars.enabled" to "false",
        "events.penguinHideAndSeek.enabled" to "false",
        "storage.autoSave.minutes" to "0",
        "spawns.npcs" to "tzhaar_city.npc-spawns.toml",
        "spawns.items" to "tzhaar_city.items.toml",
        "spawns.objects" to "tzhaar_city.objs.toml",
    )

fun loadRuntimeSettings(runtimeMode: RuntimeMode, overrides: Map<String, String> = emptyMap()): Properties = timed("properties") {
    val properties = Settings.load()
    properties.putAll(System.getenv())
    if (runtimeMode == RuntimeMode.Headless) {
        properties.putAll(headlessPrunedDefaults)
    }
    properties.putAll(overrides)
    properties[RUNTIME_MODE_KEY] = runtimeMode.id
    properties
}

fun preloadRuntime(
    cache: Cache,
    configFiles: ConfigFiles,
    runtimeMode: RuntimeMode,
    extraModules: List<Module> = emptyList(),
    loadContentScripts: Boolean = true,
    installShutdownHook: Boolean = true,
    scriptAllowlist: Set<String>? = null,
): List<Script> {
    prepareRuntimeStateFiles()
    configFiles.update()
    stopKoin()
    startKoin {
        slf4jLogger(level = Level.ERROR)
        modules(
            listOf(
                engineModule(configFiles),
                gameModule(configFiles),
                cacheModule(cache, configFiles),
            ) + extraModules,
        )
    }
    engineLoad(configFiles)
    Wildcards.load(Settings["storage.wildcards"])
    val scripts = if (loadContentScripts) ContentLoader().load(scriptAllowlist) else emptyList()
    Wildcards.update(Settings["storage.wildcards"])

    if (installShutdownHook) {
        Runtime.getRuntime().addShutdownHook(
            thread(start = false) {
                Despawn.world()
                AuditLog.save()
            },
        )
    }

    InlineLogger("RuntimeBootstrap").info { "Configured runtime mode '${runtimeMode.id}'" }
    return scripts
}

private fun prepareRuntimeStateFiles() {
    val modified = Path.of(Settings["storage.data.modified"])
    modified.parent?.let { Files.createDirectories(it) }

    val wildcards = Path.of(Settings["storage.wildcards"])
    wildcards.parent?.let { Files.createDirectories(it) }
}

fun cacheModule(cache: Cache, files: ConfigFiles): Module {
    val members = Settings["world.members", false]
    val headless = Settings["runtime.mode", RuntimeMode.Headed.id] == RuntimeMode.Headless.id
    val module =
        module {
            single(createdAtStart = true) {
                get<ObjectDefinitions>()
                MapDefinitions(CollisionDecoder(), cache).load(files)
            }
            single(createdAtStart = true) { Huffman().load(cache.data(Index.HUFFMAN, 1)!!) }
            single(createdAtStart = true) {
                ObjectDefinitions
                    .init(ObjectDecoder(members, lowDetail = false, get<ParameterDefinitions>()).load(cache))
                    .load(files.list(Settings["definitions.objects"]))
            }
            single(createdAtStart = true) {
                val npcDefinitions =
                    NPCDefinitions
                        .init(NPCDecoder(members, get<ParameterDefinitions>()).load(cache))
                if (headless) {
                    npcDefinitions.load(files.list(Settings["definitions.npcs"]), null)
                } else {
                    npcDefinitions.load(files.list(Settings["definitions.npcs"]), get())
                }
            }
            single(createdAtStart = true) {
                ItemDefinitions
                    .init(ItemDecoder(get<ParameterDefinitions>()).load(cache))
                    .load(files.list(Settings["definitions.items"]))
            }
            single(createdAtStart = true) { AnimationDefinitions(AnimationDecoder().load(cache)).load(files.list(Settings["definitions.animations"])) }
            single(createdAtStart = true) {
                get<ItemDefinitions>()
                get<InterfaceDefinitions>()
                get<InventoryDefinitions>()
                get<NPCDefinitions>()
                get<StructDefinitions>()
                get<ObjectDefinitions>()
                EnumDefinitions.init(EnumDecoder().load(cache)).load(files.list(Settings["definitions.enums"]))
            }
            single(createdAtStart = true) { GraphicDefinitions(GraphicDecoder().load(cache)).load(files.list(Settings["definitions.graphics"])) }
            single(createdAtStart = true) {
                InterfaceDefinitions
                    .init(InterfaceDecoder().load(cache))
                    .load(files.list(Settings["definitions.interfaces"]), files.find(Settings["definitions.interfaces.types"]))
            }
            single(createdAtStart = true) {
                get<ItemDefinitions>()
                InventoryDefinitions
                    .init(InventoryDecoder().load(cache))
                    .load(files.list(Settings["definitions.inventories"]), files.list(Settings["definitions.shops"]))
            }
            single(createdAtStart = true) {
                StructDefinitions
                    .init(StructDecoder(get<ParameterDefinitions>()).load(cache))
                    .load(files.find(Settings["definitions.structs"]))
            }
            single(createdAtStart = true) { QuickChatPhraseDefinitions(QuickChatPhraseDecoder().load(cache)).load() }
            single(createdAtStart = true) { WeaponStyleDefinitions().load(files.find(Settings["definitions.weapons.styles"])) }
            single(createdAtStart = true) { WeaponAnimationDefinitions().load(files.find(Settings["definitions.weapons.animations"])) }
            single(createdAtStart = true) { AmmoDefinitions().load(files.find(Settings["definitions.ammoGroups"])) }
            single(createdAtStart = true) { ParameterDefinitions(get(), get()).load(files.find(Settings["definitions.parameters"])) }
            single(createdAtStart = true) { FontDefinitions(FontDecoder().load(cache)).load(files.find(Settings["definitions.fonts"])) }
            single(createdAtStart = true) {
                get<ItemDefinitions>()
                ItemOnItemDefinitions().load(files.list(Settings["definitions.itemOnItem"]))
            }
            single(createdAtStart = true) {
                VariableDefinitions().load(
                    files.list(Settings["definitions.variables.players"]),
                    files.list(Settings["definitions.variables.bits"]),
                    files.list(Settings["definitions.variables.clients"]),
                    files.list(Settings["definitions.variables.strings"]),
                    files.list(Settings["definitions.variables.customs"]),
                )
            }
            single(createdAtStart = true) {
                if (headless) {
                    DropTables().load(emptyList())
                } else {
                    get<ItemDefinitions>()
                    DropTables().load(emptyList()).load(files.list(Settings["spawns.drops"]))
                }
            }
            single(createdAtStart = true) { ObjectTeleports().load(files.list(Settings["map.teleports"])) }
        }
    return module
}

