package sim.engine.client.ui

import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.test.mock.declare
import sim.engine.data.definition.InterfaceDefinitions
import sim.engine.entity.character.player.Player
import sim.engine.script.KoinMock
import sim.network.client.Client

abstract class InterfaceTest : KoinMock() {

    internal lateinit var client: Client
    internal lateinit var player: Player
    internal lateinit var interfaces: Interfaces
    internal lateinit var open: MutableMap<String, String>

    @BeforeEach
    open fun setup() {
        client = mockk(relaxed = true)
        player = mockk(relaxed = true)
        every { player.client } returns client
        open = mutableMapOf()
        interfaces = spyk(Interfaces(player, open))
        mockkObject(InterfaceApi)
        mockkStatic("sim.network.login.protocol.encode.InterfaceEncodersKt")
        mockkStatic("sim.engine.client.ui.InterfacesKt")
    }

    @AfterEach
    fun teardown() {
        unmockkObject(InterfaceApi)
    }
}
