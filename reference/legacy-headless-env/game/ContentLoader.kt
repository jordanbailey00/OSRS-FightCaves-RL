import com.github.michaelbull.logging.InlineLogger
import content.skill.prayer.PrayerApi
import sim.engine.Script
import sim.engine.client.ui.chat.plural
import sim.engine.get
import java.nio.file.NoSuchFileException
import kotlin.system.exitProcess

/**
 * Loads content scripts from a precomputed scripts.txt list made by scriptMetadata gradle build task
 */
class ContentLoader {
    private val logger = InlineLogger()

    fun load(allowedScripts: Set<String>? = null): List<Script> {
        val start = System.currentTimeMillis()
        val available = availableScriptClasses()
        val scriptNames =
            if (allowedScripts == null) {
                available
            } else {
                val missing = allowedScripts - available.toSet()
                require(missing.isEmpty()) { "Headless script allowlist contains unknown script classes: ${missing.sorted().joinToString()}" }
                available.filter { it in allowedScripts }
            }
        loadContentApis()
        val scripts = mutableListOf<Script>()
        var script = ""
        try {
            for (name in scriptNames) {
                script = name
                scripts.add(loadScript(name) as Script)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load script: $script" }
            logger.error { "If the file exists make sure the scripts package is correct." }
            logger.error { "If the file has been deleted try running 'gradle cleanScriptMetadata'." }
            logger.error { "Otherwise make sure the return type is written explicitly." }
            exitProcess(1)
        }
        if (scripts.isEmpty()) {
            throw NoSuchFileException("No content scripts found.")
        }
        logger.info {
            val mode = if (allowedScripts == null) "all" else "allowlisted"
            "Loaded ${scripts.size} ${"script".plural(scripts.size)} in ${System.currentTimeMillis() - start}ms ($mode)"
        }
        return scripts
    }

    fun availableScriptClasses(): List<String> {
        val reader =
            ContentLoader::class.java.getResourceAsStream("scripts.txt")?.bufferedReader()
                ?: error("No auto-generated script file found, make sure 'gradle scriptMetadata' is correctly running")
        return reader.useLines { lines ->
            lines
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { it.substringAfterLast("|") }
                .toList()
        }
    }

    private fun loadContentApis() {
        Script.interfaces.add(PrayerApi)
    }

    private fun loadScript(name: String): Any {
        val clazz = Class.forName(name)
        val constructor = clazz.declaredConstructors.first()
        val params = constructor.parameters.map { get(it.type.kotlin) }.toTypedArray()
        return constructor.newInstance(*params)
    }
}
