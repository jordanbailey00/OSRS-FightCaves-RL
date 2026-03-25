package content.area.karamja.tzhaar_city

import content.skill.prayer.protectMagic
import content.skill.prayer.protectMelee
import content.skill.prayer.protectRange
import sim.engine.GameLoop
import sim.engine.entity.character.Character
import sim.engine.entity.character.npc.NPC
import sim.engine.entity.character.player.Player
import sim.engine.queue.strongQueue
import sim.engine.timer.CLIENT_TICKS

enum class JadTelegraphState(val encoded: Int) {
    Idle(0),
    MagicWindup(1),
    RangedWindup(2),
}

enum class JadCommittedAttackStyle(val attackId: String) {
    None(""),
    Magic("magic"),
    Ranged("range"),
}

data class JadTelegraphTrace(
    val attackSequence: Int,
    val committedAttackStyle: String,
    val telegraphState: String,
    val telegraphStateCode: Int,
    val telegraphStartTick: Int,
    val hitResolveTick: Int,
    val prayerCheckTick: Int,
    val sampledProtectionPrayer: String,
    val protectedAtPrayerCheck: Boolean,
    val resolvedDamage: Int,
) {
    fun toOrderedMap(): LinkedHashMap<String, Any> =
        linkedMapOf(
            "attack_sequence" to attackSequence,
            "committed_attack_style" to committedAttackStyle,
            "telegraph_state" to telegraphState,
            "telegraph_state_code" to telegraphStateCode,
            "telegraph_start_tick" to telegraphStartTick,
            "hit_resolve_tick" to hitResolveTick,
            "prayer_check_tick" to prayerCheckTick,
            "sampled_protection_prayer" to sampledProtectionPrayer,
            "protected_at_prayer_check" to protectedAtPrayerCheck,
            "resolved_damage" to resolvedDamage,
        )
}

const val JAD_HIT_TARGET_QUEUE_TICKS = 3
const val JAD_HIT_CLIENT_DELAY = 64

// Preserve the current headed Jad reaction window: 3 queued ticks plus the existing hit delay path.
val JAD_HIT_RESOLVE_OFFSET_TICKS: Int = JAD_HIT_TARGET_QUEUE_TICKS + CLIENT_TICKS.toTicks(JAD_HIT_CLIENT_DELAY) + 1

private const val JAD_TELEGRAPH_STATE_KEY = "jad_telegraph_state"
private const val JAD_TELEGRAPH_START_TICK_KEY = "jad_telegraph_start_tick"
private const val JAD_HIT_RESOLVE_TICK_KEY = "jad_hit_resolve_tick"
private const val JAD_LAST_TELEGRAPH_START_TICK_KEY = "jad_last_telegraph_start_tick"
private const val JAD_LAST_HIT_RESOLVE_TICK_KEY = "jad_last_hit_resolve_tick"
private const val JAD_COMMITTED_ATTACK_STYLE_KEY = "jad_committed_attack_style"
private const val JAD_ATTACK_SEQUENCE_KEY = "jad_attack_sequence"
private const val JAD_PRAYER_CHECK_TICK_KEY = "jad_prayer_check_tick"
private const val JAD_SAMPLED_PROTECTION_PRAYER_KEY = "jad_sampled_protection_prayer"
private const val JAD_PROTECTED_AT_PRAYER_CHECK_KEY = "jad_protected_at_prayer_check"
private const val JAD_RESOLVED_DAMAGE_KEY = "jad_resolved_damage"
private const val JAD_TELEGRAPH_CLEAR_QUEUE = "jad_telegraph_clear"

fun jadCommittedAttackStyleForAttackId(attackId: String): JadCommittedAttackStyle? =
    when (attackId) {
        JadCommittedAttackStyle.Magic.attackId -> JadCommittedAttackStyle.Magic
        JadCommittedAttackStyle.Ranged.attackId -> JadCommittedAttackStyle.Ranged
        else -> null
    }

fun jadTelegraphStateForStyle(style: JadCommittedAttackStyle): JadTelegraphState =
    when (style) {
        JadCommittedAttackStyle.Magic -> JadTelegraphState.MagicWindup
        JadCommittedAttackStyle.Ranged -> JadTelegraphState.RangedWindup
        JadCommittedAttackStyle.None -> JadTelegraphState.Idle
    }

fun JadTelegraphState.serializedName(): String =
    when (this) {
        JadTelegraphState.Idle -> "idle"
        JadTelegraphState.MagicWindup -> "magic_windup"
        JadTelegraphState.RangedWindup -> "ranged_windup"
    }

fun JadCommittedAttackStyle.serializedName(): String =
    when (this) {
        JadCommittedAttackStyle.None -> "none"
        JadCommittedAttackStyle.Magic -> "magic"
        JadCommittedAttackStyle.Ranged -> "range"
    }

var NPC.jadTelegraphState: JadTelegraphState
    get() = get(JAD_TELEGRAPH_STATE_KEY, JadTelegraphState.Idle)
    set(value) = set(JAD_TELEGRAPH_STATE_KEY, value)

var NPC.jadTelegraphStartTick: Int
    get() = get(JAD_TELEGRAPH_START_TICK_KEY, -1)
    set(value) = set(JAD_TELEGRAPH_START_TICK_KEY, value)

var NPC.jadHitResolveTick: Int
    get() = get(JAD_HIT_RESOLVE_TICK_KEY, -1)
    set(value) = set(JAD_HIT_RESOLVE_TICK_KEY, value)

var NPC.jadCommittedAttackStyle: JadCommittedAttackStyle
    get() = get(JAD_COMMITTED_ATTACK_STYLE_KEY, JadCommittedAttackStyle.None)
    set(value) = set(JAD_COMMITTED_ATTACK_STYLE_KEY, value)

var NPC.jadAttackSequence: Int
    get() = get(JAD_ATTACK_SEQUENCE_KEY, 0)
    set(value) = set(JAD_ATTACK_SEQUENCE_KEY, value)

var NPC.jadPrayerCheckTick: Int
    get() = get(JAD_PRAYER_CHECK_TICK_KEY, -1)
    set(value) = set(JAD_PRAYER_CHECK_TICK_KEY, value)

var NPC.jadSampledProtectionPrayer: String
    get() = get(JAD_SAMPLED_PROTECTION_PRAYER_KEY, "none")
    set(value) = set(JAD_SAMPLED_PROTECTION_PRAYER_KEY, value)

var NPC.jadProtectedAtPrayerCheck: Boolean
    get() = get(JAD_PROTECTED_AT_PRAYER_CHECK_KEY, false)
    set(value) = set(JAD_PROTECTED_AT_PRAYER_CHECK_KEY, value)

var NPC.jadResolvedDamage: Int
    get() = get(JAD_RESOLVED_DAMAGE_KEY, -1)
    set(value) = set(JAD_RESOLVED_DAMAGE_KEY, value)

private var NPC.jadLastTelegraphStartTick: Int
    get() = get(JAD_LAST_TELEGRAPH_START_TICK_KEY, -1)
    set(value) = set(JAD_LAST_TELEGRAPH_START_TICK_KEY, value)

private var NPC.jadLastHitResolveTick: Int
    get() = get(JAD_LAST_HIT_RESOLVE_TICK_KEY, -1)
    set(value) = set(JAD_LAST_HIT_RESOLVE_TICK_KEY, value)

fun NPC.beginJadTelegraph(style: JadCommittedAttackStyle, onsetTick: Int = GameLoop.tick) {
    require(id == "tztok_jad") { "Jad telegraph state can only be started for tztok_jad, got '$id'." }
    require(style != JadCommittedAttackStyle.None) { "Jad telegraph requires a committed magic or ranged attack style." }

    val sequence = jadAttackSequence + 1
    jadAttackSequence = sequence
    jadCommittedAttackStyle = style
    jadTelegraphState = jadTelegraphStateForStyle(style)
    jadTelegraphStartTick = onsetTick
    jadHitResolveTick = onsetTick + JAD_HIT_RESOLVE_OFFSET_TICKS
    jadLastTelegraphStartTick = onsetTick
    jadLastHitResolveTick = jadHitResolveTick
    jadPrayerCheckTick = -1
    jadSampledProtectionPrayer = "none"
    jadProtectedAtPrayerCheck = false
    jadResolvedDamage = -1

    strongQueue(JAD_TELEGRAPH_CLEAR_QUEUE, JAD_HIT_RESOLVE_OFFSET_TICKS) {
        if (jadAttackSequence == sequence) {
            clearJadTelegraph()
        }
    }
}

fun NPC.beginJadTelegraphForAttack(attackId: String): Boolean {
    val style = jadCommittedAttackStyleForAttackId(attackId) ?: return false
    beginJadTelegraph(style)
    return true
}

fun NPC.clearJadTelegraph() {
    jadTelegraphState = JadTelegraphState.Idle
    jadCommittedAttackStyle = JadCommittedAttackStyle.None
    jadTelegraphStartTick = -1
    jadHitResolveTick = -1
}

fun NPC.recordJadAttackOutcome(target: Character, resolvedDamage: Int, prayerCheckTick: Int = GameLoop.tick) {
    require(id == "tztok_jad") { "Jad outcome recording can only be used for tztok_jad, got '$id'." }

    jadPrayerCheckTick = prayerCheckTick
    jadSampledProtectionPrayer = sampledProtectionPrayer(target)
    jadProtectedAtPrayerCheck =
        when (jadCommittedAttackStyle) {
            JadCommittedAttackStyle.Magic -> target.protectMagic()
            JadCommittedAttackStyle.Ranged -> target.protectRange()
            JadCommittedAttackStyle.None -> false
        }
    jadResolvedDamage = resolvedDamage
}

fun NPC.jadTelegraphTraceOrNull(): JadTelegraphTrace? {
    if (id != "tztok_jad") {
        return null
    }
    return JadTelegraphTrace(
        attackSequence = jadAttackSequence,
        committedAttackStyle = jadCommittedAttackStyle.serializedName(),
        telegraphState = jadTelegraphState.serializedName(),
        telegraphStateCode = jadTelegraphState.encoded,
        telegraphStartTick = if (jadTelegraphStartTick != -1) jadTelegraphStartTick else jadLastTelegraphStartTick,
        hitResolveTick = if (jadHitResolveTick != -1) jadHitResolveTick else jadLastHitResolveTick,
        prayerCheckTick = jadPrayerCheckTick,
        sampledProtectionPrayer = jadSampledProtectionPrayer,
        protectedAtPrayerCheck = jadProtectedAtPrayerCheck,
        resolvedDamage = jadResolvedDamage,
    )
}

private fun sampledProtectionPrayer(target: Character): String =
    when (target) {
        is Player ->
            when {
                target.protectMagic() -> "protect_from_magic"
                target.protectRange() -> "protect_from_missiles"
                target.protectMelee() -> "protect_from_melee"
                else -> "none"
            }

        else -> "none"
    }
