package sim.cache.config.data

import sim.cache.Definition

data class PlayerVariableParameterDefinition(
    override var id: Int = -1,
    var type: Int = 0,
) : Definition
