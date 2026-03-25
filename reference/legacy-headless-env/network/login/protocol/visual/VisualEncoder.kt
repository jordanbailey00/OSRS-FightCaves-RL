package sim.network.login.protocol.visual

import sim.buffer.write.Writer

abstract class VisualEncoder<V : Visuals>(
    val mask: Int,
    val initial: Boolean = false,
) {

    /**
     * @param index The index of the observing client
     */
    open fun encode(writer: Writer, visuals: V, index: Int) {
        encode(writer, visuals)
    }

    abstract fun encode(writer: Writer, visuals: V)
}
