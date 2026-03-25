package content.social.report

import sim.engine.Script
import sim.engine.client.message
import sim.engine.client.ui.hasMenuOpen
import sim.engine.client.ui.open

class ReportAbuse : Script {

    init {
        interfaceOption("Report Abuse", "filter_buttons:report") {
            if (hasMenuOpen()) {
                message("Please finish what you're doing first.")
                return@interfaceOption
            }
            open("report_abuse_player_security")
        }
    }
}
