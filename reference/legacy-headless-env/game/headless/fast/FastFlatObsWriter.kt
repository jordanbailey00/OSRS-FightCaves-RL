package headless.fast

object FastFlatObsWriter {

    fun copyBatch(
        source: FloatArray,
        envCount: Int,
        featureCount: Int,
        target: FloatArray? = null,
    ): FloatArray {
        val expectedSize = envCount * featureCount
        require(source.size == expectedSize) {
            "Flat observation source size mismatch: expected $expectedSize, got ${source.size}."
        }
        val destination =
            if (target == null) {
                FloatArray(expectedSize)
            } else {
                require(target.size == expectedSize) {
                    "Flat observation target size mismatch: expected $expectedSize, got ${target.size}."
                }
                target
            }
        source.copyInto(destination)
        return destination
    }
}
