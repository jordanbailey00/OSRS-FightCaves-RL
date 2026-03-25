import sim.config.ConfigReader
import sim.engine.Script
import sim.engine.data.Settings
import sim.engine.entity.Despawn
import sim.engine.entity.Operation
import sim.engine.entity.Spawn
import sim.engine.entity.character.Death
import sim.engine.entity.character.mode.combat.CombatApi
import sim.engine.entity.character.mode.move.Moved
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.Skills
import sim.engine.timer.TimerApi
import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

private const val HEADLESS_SCRIPT_ALLOWLIST_PATH_KEY = "headless.scripts.allowlist.path"
private const val DEFAULT_HEADLESS_SCRIPT_ALLOWLIST_PATH = "config/headless_scripts.txt"
private const val HEADLESS_MANIFEST_PATH_KEY = "headless.manifest.path"
private const val DEFAULT_HEADLESS_MANIFEST_PATH = "config/headless_manifest.toml"

data class HeadlessScriptAllowlist(
    val repositoryRoot: Path,
    val sourcePath: Path,
    val classNames: List<String>,
)

fun loadHeadlessScriptAllowlist(path: String = Settings[HEADLESS_SCRIPT_ALLOWLIST_PATH_KEY, DEFAULT_HEADLESS_SCRIPT_ALLOWLIST_PATH]): HeadlessScriptAllowlist {
    val root = locateRepositoryRoot()
    val source = resolvePath(root, path)
    require(source.isRegularFile()) { "Headless script allowlist file not found: $source" }

    val classNames =
        Files.readAllLines(source)
            .asSequence()
            .map { it.replace("\uFEFF", "").trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.substringAfterLast("|") }
            .distinct()
            .toList()

    require(classNames.isNotEmpty()) { "Headless script allowlist must contain at least one script class: $source" }

    return HeadlessScriptAllowlist(
        repositoryRoot = root,
        sourcePath = source,
        classNames = classNames,
    )
}

fun validateHeadlessScriptRuntime(allowlist: HeadlessScriptAllowlist, loadedScripts: List<Script>) {
    val loadedClasses = loadedScripts.map(::scriptClassName).toSet()
    val allowlistedClasses = allowlist.classNames.toSet()

    val missingLoaded = allowlistedClasses - loadedClasses
    require(missingLoaded.isEmpty()) {
        "Headless runtime missing allowlisted scripts: ${missingLoaded.sorted().joinToString()}"
    }

    val unexpectedLoaded = loadedClasses - allowlistedClasses
    require(unexpectedLoaded.isEmpty()) {
        "Headless runtime loaded scripts outside allowlist: ${unexpectedLoaded.sorted().joinToString()}"
    }

    validateManifestScriptParity(allowlist)
    validateFightCaveScriptHooks()
}

private fun scriptClassName(script: Script): String = script::class.qualifiedName ?: script::class.java.name

private fun validateManifestScriptParity(allowlist: HeadlessScriptAllowlist) {
    val requiredByManifest = loadManifestRequiredScriptClasses(allowlist.repositoryRoot)
    val allowlistedClasses = allowlist.classNames.toSet()

    val missingFromAllowlist = requiredByManifest - allowlistedClasses
    require(missingFromAllowlist.isEmpty()) {
        "Headless script allowlist is missing manifest-required scripts: ${missingFromAllowlist.sorted().joinToString()}"
    }

    val unknownToManifest = allowlistedClasses - requiredByManifest
    require(unknownToManifest.isEmpty()) {
        "Headless script allowlist contains scripts not declared in manifest: ${unknownToManifest.sorted().joinToString()}"
    }
}

private fun loadManifestRequiredScriptClasses(repositoryRoot: Path): Set<String> {
    val path = Settings[HEADLESS_MANIFEST_PATH_KEY, DEFAULT_HEADLESS_MANIFEST_PATH]
    val source = resolvePath(repositoryRoot, path)
    require(Files.isRegularFile(source)) { "Headless manifest file not found for script drift guard: $source" }

    val sections =
        BufferedInputStream(Files.newInputStream(source)).use { stream ->
            ConfigReader(stream, 4096, source.toString()).use { reader ->
                reader.sections()
            }
        }

    val scriptSection = sections["scripts"] ?: error("Missing [scripts] section in headless manifest: $source")
    val required = scriptSection["required_classes"] as? List<*> ?: error("Missing [scripts].required_classes in headless manifest: $source")

    return required.map {
        it as? String ?: error("Expected [scripts].required_classes to contain only strings: $source")
    }.toSet()
}

private fun validateFightCaveScriptHooks() {
    requireHook(
        condition = Operation.playerObject.containsKey("Enter:cave_entrance_fight_cave"),
        message = "Missing fight cave entry object operation hook: Enter:cave_entrance_fight_cave",
    )
    requireHook(
        condition = Operation.playerObject.containsKey("Enter:cave_exit_fight_cave"),
        message = "Missing fight cave exit object operation hook: Enter:cave_exit_fight_cave",
    )
    requireHook(
        condition = Spawn.hasWorldSpawnHandlers(),
        message = "No world spawn handlers registered; fight cave wave loader cannot initialize.",
    )
    requireHook(
        condition = Despawn.hasNpcDespawnHandler("tztok_jad"),
        message = "Missing NPC despawn hook required for fight cave wave progression.",
    )
    requireHook(
        condition = Death.hasNpcDeathHandler("tz_kek"),
        message = "Missing NPC death hook required for Tz-Kek split handling.",
    )
    requireHook(
        condition = CombatApi.hasNpcDamageHandler("tz_kek", "*"),
        message = "Missing NPC combat damage hook for tz_kek.",
    )
    requireHook(
        condition = CombatApi.hasNpcDamageHandler("tz_kek_spawn_point", "*"),
        message = "Missing NPC combat damage hook for tz_kek_spawn_point.",
    )
    requireHook(
        condition = CombatApi.hasNpcAttackHandler("tztok_jad", "magic"),
        message = "Missing NPC attack hook for tztok_jad magic attack.",
    )
    requireHook(
        condition = CombatApi.hasNpcAttackHandler("tztok_jad", "range"),
        message = "Missing NPC attack hook for tztok_jad range attack.",
    )
    requireHook(
        condition = CombatApi.hasNpcConditionHandler("weakened_nearby_monsters"),
        message = "Missing healer NPC condition hook: weakened_nearby_monsters.",
    )
    requireHook(
        condition = Skills.hasNpcLevelChangedHandler("tztok_jad", Skill.Constitution),
        message = "Missing NPC level change hook for tztok_jad constitution transitions.",
    )
    requireHook(
        condition = Moved.hasNpcMovedHandler("yt_hur_kot"),
        message = "Missing NPC moved hook for yt_hur_kot healer behavior.",
    )
    requireHook(
        condition = TimerApi.hasNpcTimerStartHandler("yt_hur_kot_heal"),
        message = "Missing NPC timer start hook for yt_hur_kot_heal.",
    )
    requireHook(
        condition = TimerApi.hasNpcTimerTickHandler("yt_hur_kot_heal"),
        message = "Missing NPC timer tick hook for yt_hur_kot_heal.",
    )
}

private fun requireHook(condition: Boolean, message: String) {
    if (!condition) {
        throw IllegalStateException(message)
    }
}

