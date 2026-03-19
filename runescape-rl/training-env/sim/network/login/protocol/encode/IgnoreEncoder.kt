package sim.network.login.protocol.encode

import io.ktor.utils.io.*
import sim.network.client.Client
import sim.network.client.Client.Companion.string
import sim.network.login.Protocol.UNLOCK_IGNORES
import sim.network.login.Protocol.UPDATE_IGNORE
import sim.network.login.protocol.writeByte
import sim.network.login.protocol.writeText

fun Client.updateIgnoreList(name: String, previous: String, renamed: Boolean) {
    send(UPDATE_IGNORE, 1 + count(name, previous), Client.BYTE) {
        writeByte(renamed)
        writeNames(name, previous)
    }
}

private fun String.format() = lowercase().replace(" ", "_")

fun Client.sendIgnoreList(ignores: List<Pair<String, String>>) {
    send(UNLOCK_IGNORES, 1 + ignores.sumOf { count(it.first, it.second) }, Client.SHORT) {
        writeByte(ignores.size)

        for ((name, previous) in ignores) {
            writeNames(name, previous)
        }
    }
}

private suspend fun ByteWriteChannel.writeNames(name: String, previous: String) {
    writeText(name)
    var formatted = name.format()
    writeText(if (previous == formatted) "" else formatted)
    writeText(previous)
    formatted = previous.format()
    writeText(if (previous == formatted) "" else formatted)
}

private fun count(name: String, previous: String): Int {
    var count = string(name) + string(previous)
    var formatted = name.format()
    count += string(if (previous == formatted) "" else formatted)
    formatted = previous.format()
    count += string(if (previous == formatted) "" else formatted)
    return count
}
