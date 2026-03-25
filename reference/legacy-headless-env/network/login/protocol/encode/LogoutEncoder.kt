package sim.network.login.protocol.encode

import sim.network.client.Client
import sim.network.login.Protocol.LOGOUT

fun Client.logout() = send(LOGOUT) {}
