package sim.engine.entity.character.player.skill.exp

import sim.engine.data.Settings
import sim.engine.entity.character.player.Player
import sim.engine.entity.character.player.skill.Skill
import sim.engine.entity.character.player.skill.level.Level
import sim.engine.entity.character.player.skill.Skills
import sim.engine.event.AuditLog

class Experience(
    val experience: IntArray = defaultExperience.clone(),
    val blocked: MutableSet<Skill> = mutableSetOf(),
) {

    lateinit var player: Player

    fun direct(skill: Skill): Int = experience[skill.ordinal]

    fun get(skill: Skill): Double = experience[skill.ordinal] / 10.0

    fun set(skill: Skill, experience: Double) {
        set(skill, (experience * 10.0).toInt())
    }

    fun set(skill: Skill, experience: Int) {
        if (experience in 0..MAXIMUM_EXPERIENCE && !blocked.contains(skill)) {
            val previous = direct(skill)
            this.experience[skill.ordinal] = experience
            update(skill, previous)
            AuditLog.event(player, "set_exp", skill, experience)
        }
    }

    fun update(skill: Skill, previous: Int = direct(skill)) {
        val experience = direct(skill)
        Skills.exp(player, skill, previous, experience)
    }

    fun add(skill: Skill, experience: Double) {
        if (experience <= 0.0) {
            return
        }
        val actual = experience * 10 * Settings["world.experienceRate", DEFAULT_EXPERIENCE_RATE]
        if (blocked.contains(skill)) {
            Skills.blocked(player, skill, actual.toInt())
        } else {
            val current = direct(skill)
            AuditLog.event(player, "exp", skill, experience)
            set(skill, current + actual.toInt())
        }
    }

    fun addBlock(skill: Skill) {
        blocked.add(skill)
    }

    fun blocked(skill: Skill) = blocked.contains(skill)

    fun removeBlock(skill: Skill) {
        blocked.remove(skill)
    }

    companion object {
        const val DEFAULT_EXPERIENCE_RATE = 1.0
        const val MAXIMUM_EXPERIENCE = 2_000_000_000
        val defaultExperience = IntArray(Skill.count) {
            if (it == Skill.Constitution.ordinal) 11540 else 0
        }

        /**
         * E1.2: Precomputed cumulative XP thresholds for levels 1..120.
         * xpThresholds[i] = total / 4 at level i+1 (the XP needed to reach level i+2).
         * Used by [level] via binary search instead of O(99) Math.pow loop.
         */
        /**
         * E1.2: Precomputed cumulative XP thresholds for levels 1..120.
         * Uses integer division (total / 4) to match the original code exactly.
         * xpThresholds[i] = total / 4 at level i+1 — the boundary checked by the
         * original `experience < total / 4` condition.
         */
        private val xpThresholds: IntArray = run {
            val arr = IntArray(120)
            var total = 0
            for (lvl in 1..120) {
                total += Level.experienceAt(lvl)
                arr[lvl - 1] = total / 4
            }
            arr
        }

        fun level(skill: Skill, experience: Double): Int {
            val maxLevel = if (skill == Skill.Dungeoneering) 120 else 99
            // Binary search matching the original: `experience < total / 4` returns level
            // So: threshold[i] is NOT passed when experience < threshold[i]
            var lo = 0
            var hi = maxLevel - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (experience >= xpThresholds[mid]) {
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
            val rawLevel = (lo + 1).coerceAtMost(maxLevel)
            return if (skill == Skill.Constitution) rawLevel * 10 else rawLevel
        }
    }
}

fun Player.exp(skill: Skill, experience: Double) = this.experience.add(skill, experience)
