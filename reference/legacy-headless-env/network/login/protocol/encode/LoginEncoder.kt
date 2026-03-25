package sim.network.login.protocol.encode

import sim.network.Response
import sim.network.client.Client
import sim.network.login.protocol.writeByte
import sim.network.login.protocol.writeMedium
import sim.network.login.protocol.writeShort
import sim.network.login.protocol.writeText

fun Client.login(username: String, index: Int, rights: Int, member: Boolean = true, membersWorld: Boolean = true) = send(-1) {
    writeByte(Response.SUCCESS)
    writeByte(13 + Client.string(username))
    writeByte(rights)
    writeByte(0) // Unknown - something to do with skipping chat messages
    writeByte(0)
    writeByte(0)
    writeByte(0)
    writeByte(0) // Moves chat box position
    writeShort(index)
    writeByte(member)
    writeMedium(0)
    writeByte(membersWorld)
    writeText(username)
    flush()
}
