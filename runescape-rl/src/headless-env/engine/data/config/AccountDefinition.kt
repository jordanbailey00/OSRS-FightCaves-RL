package sim.engine.data.config

data class AccountDefinition(
    val accountName: String,
    var displayName: String,
    var previousName: String,
    var passwordHash: String,
)
