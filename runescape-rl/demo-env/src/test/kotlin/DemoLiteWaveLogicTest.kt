import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DemoLiteWaveLogicTest {
    @Test
    fun `player death maps to shared terminal code`() {
        val observation = observation(hitpointsCurrent = 0)

        val terminal =
            DemoLiteWaveLogic.evaluateTerminalCode(
                observation = observation,
                inFightCaveInstance = true,
                episodeStartTick = 10,
                tickCap = 20_000,
            )

        assertEquals(DemoLiteTerminalCode.PLAYER_DEATH, terminal)
    }

    @Test
    fun `wave 63 clear maps to cave complete`() {
        val observation = observation(wave = 63, remaining = 0)

        val terminal =
            DemoLiteWaveLogic.evaluateTerminalCode(
                observation = observation,
                inFightCaveInstance = true,
                episodeStartTick = 10,
                tickCap = 20_000,
            )

        assertEquals(DemoLiteTerminalCode.CAVE_COMPLETE, terminal)
    }

    @Test
    fun `tick cap and jad telegraph helpers stay stable`() {
        val observation = observation(tick = 25, jadState = 2)

        val terminal =
            DemoLiteWaveLogic.evaluateTerminalCode(
                observation = observation,
                inFightCaveInstance = true,
                episodeStartTick = 20,
                tickCap = 5,
            )

        assertEquals(DemoLiteTerminalCode.TICK_CAP, terminal)
        assertEquals("ranged_windup", DemoLiteJadTelegraph.describe(observation))
    }

    private fun observation(
        tick: Int = 10,
        hitpointsCurrent: Int = 700,
        wave: Int = 1,
        remaining: Int = 1,
        jadState: Int = 0,
    ): HeadlessObservationV1 =
        HeadlessObservationV1(
            tick = tick,
            episodeSeed = 91_001L,
            player =
                HeadlessObservationPlayer(
                    tile = HeadlessObservationTile(2400, 5088, 0),
                    hitpointsCurrent = hitpointsCurrent,
                    hitpointsMax = 700,
                    prayerCurrent = 43,
                    prayerMax = 43,
                    runEnergy = 100,
                    runEnergyMax = 100,
                    runEnergyPercent = 100,
                    running = false,
                    protectionPrayers =
                        HeadlessObservationProtectionPrayers(
                            protectFromMagic = false,
                            protectFromMissiles = false,
                            protectFromMelee = false,
                        ),
                    lockouts =
                        HeadlessObservationLockouts(
                            attackLocked = false,
                            foodLocked = false,
                            drinkLocked = false,
                            comboLocked = false,
                            busyLocked = false,
                        ),
                    consumables =
                        HeadlessObservationConsumables(
                            sharkCount = 20,
                            prayerPotionDoseCount = 32,
                            ammoId = "adamant_bolts",
                            ammoCount = 1_000,
                        ),
                ),
            wave = HeadlessObservationWave(wave = wave, rotation = 0, remaining = remaining),
            npcs =
                listOf(
                    HeadlessObservationNpc(
                        visibleIndex = 0,
                        npcIndex = 1,
                        id = if (jadState == 0) "tz_haar_xil" else "tztok_jad",
                        tile = HeadlessObservationTile(2404, 5088, 0),
                        hitpointsCurrent = 250,
                        hitpointsMax = 250,
                        hidden = false,
                        dead = false,
                        underAttack = false,
                        jadTelegraphState = jadState,
                    )
                ),
        )
}
