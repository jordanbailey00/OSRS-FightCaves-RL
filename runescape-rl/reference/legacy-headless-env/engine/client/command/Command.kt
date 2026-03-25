package sim.engine.client.command

import sim.engine.entity.character.player.PlayerRights

/**
 * Data about a command
 */
data class Command(
    val name: String,
    val rights: PlayerRights = PlayerRights.None,
    val signatures: List<CommandSignature> = emptyList()
) {
    /**
     * Find the signature which closest matches the given [input] arguments
     */
    fun find(input: List<String>): CommandSignature? {
        val matches = signatures.mapNotNull { sig ->
            sig.score(input)?.let { score -> sig to score }
        }
        return matches.maxByOrNull { it.second }?.first
    }

    /**
     * Find the first valid signature
     */
    fun first(input: List<String>): CommandSignature? {
        return signatures.firstOrNull { it.valid(input) != null }
    }
}