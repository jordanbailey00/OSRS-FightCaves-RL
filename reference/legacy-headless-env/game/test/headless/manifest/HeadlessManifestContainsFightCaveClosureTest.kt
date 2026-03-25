package headless.manifest

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class HeadlessManifestContainsFightCaveClosureTest {

    private val manifest = HeadlessManifestLoader.load()

    @Test
    fun `manifest declares expected fight cave scope`() {
        assertEquals("fight-caves-headless", manifest.string("manifest", "name"))
        assertEquals("tzhaar_fight_cave", manifest.string("scope", "minigame"))
        assertEquals(63, manifest.int("scope", "wave_count"))
        assertEquals(15, manifest.int("scope", "rotation_count"))
    }

    @Test
    fun `manifest required code and data paths resolve`() {
        val missingCodeFiles = manifest.strings("code_paths", "required_files").filterNot(manifest::fileExists)
        assertTrue(missingCodeFiles.isEmpty(), "Missing required code files: ${missingCodeFiles.joinToString()}")

        val missingDataFiles = manifest.strings("data_files", "required").filterNot(manifest::fileExists)
        assertTrue(missingDataFiles.isEmpty(), "Missing required data files: ${missingDataFiles.joinToString()}")

        val emptyRequiredGlobs =
            manifest
                .strings("code_paths", "required_globs")
                .filter { manifest.countFilesInGlobBase(it) == 0 }
        assertTrue(emptyRequiredGlobs.isEmpty(), "Required code globs matched no files: ${emptyRequiredGlobs.joinToString()}")
    }

    @Test
    fun `manifest required script classes resolve to source files`() {
        val requiredClasses = manifest.strings("scripts", "required_classes")
        val unresolvedClasses = requiredClasses.filterNot(manifest::sourceExistsForClass)
        assertTrue(unresolvedClasses.isEmpty(), "Required script classes without source files: ${unresolvedClasses.joinToString()}")
    }

    @Test
    fun `manifest required ids resolve in data definitions`() {
        val ids =
            manifest.strings("npc_ids", "required") +
                manifest.strings("item_ids", "required_loadout") +
                manifest.strings("item_ids", "required_rewards") +
                manifest.strings("object_ids", "required")
        val missingIds = ids.filterNot(manifest::idExistsInData)
        assertTrue(missingIds.isEmpty(), "IDs not found in data TOML files: ${missingIds.joinToString()}")
    }
}
