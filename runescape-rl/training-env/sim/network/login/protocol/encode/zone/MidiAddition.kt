package sim.network.login.protocol.encode.zone

import sim.network.login.Protocol

data class MidiAddition(
    val tile: Int,
    val id: Int,
    val radius: Int,
    val repeat: Int,
    val delay: Int,
    val volume: Int,
    val speed: Int,
) : ZoneUpdate(
    Protocol.MIDI_AREA,
    Protocol.Batch.MIDI_AREA,
    8,
)
