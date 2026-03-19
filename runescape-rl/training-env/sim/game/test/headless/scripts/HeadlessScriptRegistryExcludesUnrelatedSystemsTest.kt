import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

internal class HeadlessScriptRegistryExcludesUnrelatedSystemsTest {

    @AfterEach
    fun cleanup() {
        resetHeadlessTestRuntime()
    }

    @Test
    fun `headless script registry excludes unrelated systems`() {
        val runtime = bootstrapHeadlessWithScripts(startWorld = false)
        val loaded = runtime.loadedScriptClasses

        assertTrue(loaded.isNotEmpty())

        val blockedPrefixes = listOf("content.social.", "content.activity.", "content.quest.", "content.minigame.")
        assertTrue(loaded.none { className -> blockedPrefixes.any(className::startsWith) })

        val forbiddenClasses =
            setOf(
                "content.social.chat.Chat",
                "content.social.trade.TradeRequest",
                "content.activity.shooting_star.ShootingStar",
                "content.minigame.sorceress_garden.SorceressGarden",
                "content.area.karamja.tzhaar_city.TzHaarBanker",
                "content.area.karamja.tzhaar_city.TzHaarShops",
            )
        assertTrue(loaded.intersect(forbiddenClasses).isEmpty())

        val fullScriptCount = ContentLoader().availableScriptClasses().size
        assertTrue(loaded.size < fullScriptCount, "Headless allowlist should load fewer scripts than full headed runtime.")

        runtime.tick()
    }
}
