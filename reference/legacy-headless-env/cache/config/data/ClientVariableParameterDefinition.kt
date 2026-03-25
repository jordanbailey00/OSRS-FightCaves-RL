package sim.cache.config.data

import sim.cache.Definition

data class ClientVariableParameterDefinition(
    override var id: Int = -1,
    var aChar3210: Char = 0.toChar(),
    var anInt3208: Int = 1,
) : Definition
