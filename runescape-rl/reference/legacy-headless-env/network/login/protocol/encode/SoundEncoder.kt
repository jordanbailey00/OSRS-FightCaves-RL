package sim.network.login.protocol.encode

import sim.network.client.Client
import sim.network.login.Protocol.JINGLE
import sim.network.login.Protocol.MIDI_SOUND
import sim.network.login.Protocol.PLAY_MUSIC
import sim.network.login.Protocol.SOUND_EFFECT
import sim.network.login.protocol.*

fun Client.playMusicTrack(
    music: Int,
    delay: Int = 100,
    volume: Int = 255,
) = send(PLAY_MUSIC) {
    writeByteSubtract(delay)
    writeByteSubtract(volume)
    writeShortAddLittle(music)
}

fun Client.playSoundEffect(
    sound: Int,
    delay: Int = 0,
    volume: Int = 255,
    speed: Int = 255,
    repeat: Int = 1,
) = send(SOUND_EFFECT) {
    writeShort(sound)
    writeByte(repeat)
    writeShort(delay)
    writeByte(volume)
    writeShort(speed)
}

fun Client.playMIDI(
    sound: Int,
    delay: Int = 0,
    volume: Int = 255,
    speed: Int = 255,
    repeat: Int = 1,
) = send(MIDI_SOUND) {
    writeShort(sound)
    writeByte(repeat)
    writeShort(delay)
    writeByte(volume)
    writeShort(speed)
}

fun Client.playJingle(
    effect: Int,
    volume: Int = 255,
) = send(JINGLE) {
    writeMedium(0)
    writeShortAddLittle(effect)
    writeByteInverse(volume)
}
