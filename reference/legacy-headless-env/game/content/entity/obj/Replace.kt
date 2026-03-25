package content.entity.obj

import sim.engine.data.definition.ObjectDefinitions
import sim.engine.entity.obj.GameObject
import sim.engine.entity.obj.GameObjects
import sim.types.Tile

object Replace {

    /**
     * Replaces two existing map objects with replacements provided.
     * The replacements can be temporary or permanent if [ticks] is -1
     */
    fun objects(
        firstOriginal: GameObject,
        firstReplacement: String,
        firstTile: Tile,
        firstRotation: Int,
        secondOriginal: GameObject,
        secondReplacement: String,
        secondTile: Tile,
        secondRotation: Int,
        ticks: Int,
        collision: Boolean = true,
    ) {
        val firstId = ObjectDefinitions.get(firstReplacement).id
        val secondId = ObjectDefinitions.get(secondReplacement).id
        if (firstId == -1 || secondId == -1) {
            return
        }
        val first = GameObject(firstId, firstTile, firstOriginal.shape, firstRotation)
        val second = GameObject(secondId, secondTile, secondOriginal.shape, secondRotation)
        GameObjects.remove(firstOriginal, collision)
        GameObjects.remove(secondOriginal, collision)
        GameObjects.add(first, collision)
        GameObjects.add(second, collision)
        GameObjects.timers.add(setOf(firstOriginal, secondOriginal, first, second), ticks) {
            GameObjects.remove(first, collision)
            GameObjects.remove(second, collision)
            GameObjects.add(firstOriginal, collision)
            GameObjects.add(secondOriginal, collision)
        }
    }
}
