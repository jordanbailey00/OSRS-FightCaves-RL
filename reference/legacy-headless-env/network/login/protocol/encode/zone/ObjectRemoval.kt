package sim.network.login.protocol.encode.zone

import sim.network.login.Protocol

data class ObjectRemoval(
    val tile: Int,
    val type: Int,
    val rotation: Int,
) : ZoneUpdate(
    Protocol.OBJECT_REMOVE,
    Protocol.Batch.OBJECT_REMOVE,
    2,
)
