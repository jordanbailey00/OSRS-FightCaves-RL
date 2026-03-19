package content.skill.prayer.list

import content.skill.prayer.PrayerConfigs
import content.skill.prayer.PrayerConfigs.ACTIVE_CURSES
import content.skill.prayer.PrayerConfigs.ACTIVE_PRAYERS
import content.skill.prayer.PrayerConfigs.BOOK_CURSES
import content.skill.prayer.PrayerConfigs.BOOK_PRAYERS
import content.skill.prayer.PrayerConfigs.PRAYERS
import content.skill.prayer.PrayerConfigs.QUICK_CURSES
import content.skill.prayer.PrayerConfigs.SELECTING_QUICK_PRAYERS
import content.skill.prayer.PrayerConfigs.USING_QUICK_PRAYERS
import world.gregs.voidps.engine.Script

class PrayerList : Script {

    init {
        playerSpawn {
            normalisePrayerBook()
        }

        interfaceOpened("prayer_orb") {
            normalisePrayerBook()
            sendVariable(SELECTING_QUICK_PRAYERS)
            sendVariable(USING_QUICK_PRAYERS)
        }

        interfaceOpened("prayer_list") {
            normalisePrayerBook()
            sendVariable(PRAYERS)
        }

        interfaceRefresh("prayer_list") { id ->
            val quickPrayers = get(SELECTING_QUICK_PRAYERS, false)
            if (quickPrayers) {
                interfaceOptions.unlockAll(id, "quick_prayers", 0..29)
            } else {
                interfaceOptions.unlockAll(id, "regular_prayers", 0..29)
            }
        }
    }

    private fun world.gregs.voidps.engine.entity.character.player.Player.normalisePrayerBook() {
        val current = get<String>(PRAYERS)
        if (isFightCavesDemoPrayerProfile()) {
            if (current != BOOK_PRAYERS) {
                set(PRAYERS, BOOK_PRAYERS)
            }
            clear(ACTIVE_CURSES)
            clear(QUICK_CURSES)
            if (!contains(ACTIVE_PRAYERS)) {
                clear("prayer_drain_counter")
                softTimers.stop("prayer_drain")
            }
            sendVariable(PRAYERS)
            sendVariable(ACTIVE_PRAYERS)
            sendVariable(ACTIVE_CURSES)
            sendVariable(SELECTING_QUICK_PRAYERS)
            sendVariable(USING_QUICK_PRAYERS)
            return
        }
        if (current != null && current != BOOK_CURSES && current != BOOK_PRAYERS) {
            set(PRAYERS, BOOK_PRAYERS)
        }
    }

    private fun world.gregs.voidps.engine.entity.character.player.Player.isFightCavesDemoPrayerProfile(): Boolean =
        get("fight_cave_demo_profile", false) || get("fight_cave_demo_episode", false)
}
