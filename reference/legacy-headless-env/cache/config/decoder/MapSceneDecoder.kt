package sim.cache.config.decoder

import sim.buffer.read.Reader
import sim.cache.Config.MAP_SCENES
import sim.cache.config.ConfigDecoder
import sim.cache.config.data.MapSceneDefinition

class MapSceneDecoder : ConfigDecoder<MapSceneDefinition>(MAP_SCENES) {

    override fun create(size: Int) = Array(size) { MapSceneDefinition(it) }

    override fun MapSceneDefinition.read(opcode: Int, buffer: Reader) {
        when (opcode) {
            1 -> sprite = buffer.readShort()
            2 -> colour = buffer.readUnsignedMedium()
            3 -> aBoolean1741 = true
            4 -> sprite = -1
        }
    }
}
