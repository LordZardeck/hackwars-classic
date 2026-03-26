package com.hackwars.rewrite.client.shell

import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

data class RewriteShellChromeState(
    val route: RewriteClientRoute = RewriteClientRoute.LOGIN,
    val frameTitle: String = DEFAULT_FRAME_TITLE,
    val statsVisible: Boolean = false,
    val countdownVisible: Boolean = false,
    val stats: RewriteShellStatsRailState = RewriteShellStatsRailState(),
    val countdown: RewriteShellCountdownState = RewriteShellCountdownState(),
)

data class RewriteShellStatsRailState(
    val playerIp: String = "",
    val pettyCashText: String = currencyFormatter.format(0.0),
    val bankMoneyText: String = currencyFormatter.format(0.0),
    val hackOMeterText: String = "0",
    val voteOMeterText: String = "0",
    val cpuLoadText: String = "0.0 / 0.0",
    val cpuPercent: Int = 0,
    val commodities: List<RewriteShellCommodityValue> = defaultCommodityValues(),
    val skills: List<RewriteShellSkillValue> = defaultSkillValues(),
) {
    companion object {
        fun defaultCommodityValues(): List<RewriteShellCommodityValue> {
            return listOf(
                RewriteShellCommodityValue("Duct Tape", "ducttape.png"),
                RewriteShellCommodityValue("Germanium", "germanium.png"),
                RewriteShellCommodityValue("Silicon", "silicon.png"),
                RewriteShellCommodityValue("YBCO", "YBCO.png"),
                RewriteShellCommodityValue("Plutonium", "plutonium.png"),
            )
        }

        fun defaultSkillValues(): List<RewriteShellSkillValue> {
            return listOf(
                RewriteShellSkillValue("Merchanting", "merchanting.png"),
                RewriteShellSkillValue("Attack", "attackIcon.png"),
                RewriteShellSkillValue("Watch", "watchIcon.png"),
                RewriteShellSkillValue("Scan", "scan.png"),
                RewriteShellSkillValue("Firewall", "firewallIcon.png"),
                RewriteShellSkillValue("HTTP", "http.png"),
                RewriteShellSkillValue("Redirect", "redirect.png"),
                RewriteShellSkillValue("Repair", "repair.png"),
                RewriteShellSkillValue("Total Level", "totallevel.png"),
            )
        }
    }
}

data class RewriteShellCommodityValue(
    val label: String,
    val iconName: String,
    val valueText: String = "0",
)

data class RewriteShellSkillValue(
    val label: String,
    val iconName: String,
    val level: Int = 0,
)

data class RewriteShellCountdownState(
    val text: String = "",
    val disconnected: Boolean = false,
)

class RewriteShellChromePresenter {
    private var route: RewriteClientRoute = RewriteClientRoute.LOGIN
    private var latestShellState: ClientGameSnapshot? = null
    private var acceptedPlayerIp: String? = null
    private var countdownSecondsRemaining: Int? = null
    private var disconnected: Boolean = false

    fun updateRoute(route: RewriteClientRoute) {
        this.route = route
        if (route != RewriteClientRoute.DESKTOP) {
            countdownSecondsRemaining = null
            disconnected = false
        }
    }

    fun updateAcceptedPlayerIp(playerIp: String?) {
        acceptedPlayerIp = playerIp?.takeIf { it.isNotBlank() }
    }

    fun updateShellState(shellState: ClientGameSnapshot?) {
        latestShellState = shellState
        val nextCountdown = shellState?.runtime?.countdownSeconds ?: 0
        if (nextCountdown > 0) {
            countdownSecondsRemaining = nextCountdown
            disconnected = false
        } else if (!disconnected) {
            countdownSecondsRemaining = null
        }
    }

    fun tick() {
        if (route != RewriteClientRoute.DESKTOP) {
            return
        }
        val remaining = countdownSecondsRemaining ?: return
        if (remaining <= 1) {
            countdownSecondsRemaining = null
            disconnected = true
            return
        }
        countdownSecondsRemaining = remaining - 1
    }

    fun currentState(): RewriteShellChromeState {
        if (route != RewriteClientRoute.DESKTOP) {
            return RewriteShellChromeState(route = route)
        }

        val playerIp = latestShellState?.identity?.playerIp
            ?.takeIf { it.isNotBlank() }
            ?: acceptedPlayerIp
            .orEmpty()
        val countdown = countdownState()
        return RewriteShellChromeState(
            route = route,
            frameTitle = frameTitle(playerIp, countdown),
            statsVisible = true,
            countdownVisible = countdown.text.isNotBlank(),
            stats = statsRailState(playerIp),
            countdown = countdown,
        )
    }

    private fun statsRailState(playerIp: String): RewriteShellStatsRailState {
        val shellState = latestShellState
        val economy = shellState?.economy
        val website = shellState?.website
        val runtime = shellState?.runtime
        val hardware = shellState?.hardware
        val stats = shellState?.stats
        return RewriteShellStatsRailState(
            playerIp = playerIp,
            pettyCashText = currencyFormatter.format(economy?.pettyCash ?: 0.0),
            bankMoneyText = currencyFormatter.format(economy?.bankMoney ?: 0.0),
            hackOMeterText = "0",
            voteOMeterText = "${website?.voteCount ?: 0}",
            cpuLoadText = "${numberFormatter.format(runtime?.currentCpuLoad ?: 0.0)} / ${numberFormatter.format(hardware?.cpuMax ?: 0.0)}",
            cpuPercent = cpuPercent(
                currentCpuLoad = runtime?.currentCpuLoad ?: 0.0,
                cpuMax = hardware?.cpuMax ?: 0.0,
            ),
            commodities = commodityValues(economy?.commodities.orEmpty()),
            skills = skillValues(stats?.experienceByFamily.orEmpty(), stats?.totalLevel ?: 0),
        )
    }

    private fun countdownState(): RewriteShellCountdownState {
        countdownSecondsRemaining?.let { remaining ->
            return RewriteShellCountdownState(
                text = "Server Shutdown in ${formatCountdown(remaining)}",
                disconnected = false,
            )
        }
        if (disconnected) {
            return RewriteShellCountdownState(
                text = "Disconnected",
                disconnected = true,
            )
        }
        return RewriteShellCountdownState()
    }

    private fun frameTitle(playerIp: String, countdown: RewriteShellCountdownState): String {
        if (countdown.text.isNotBlank()) {
            return countdown.text
        }
        return if (playerIp.isNotBlank()) {
            "Hack Wars - $playerIp"
        } else {
            DEFAULT_FRAME_TITLE
        }
    }

    private fun commodityValues(values: List<Double>): List<RewriteShellCommodityValue> {
        return RewriteShellStatsRailState.defaultCommodityValues().mapIndexed { index, base ->
            base.copy(valueText = "${values.getOrNull(index)?.toInt() ?: 0}")
        }
    }

    private fun skillValues(experienceByFamily: Map<String, Double>, totalLevel: Int): List<RewriteShellSkillValue> {
        return listOf(
            RewriteShellSkillValue("Merchanting", "merchanting.png", legacyLevelForXp(experienceByFamily["BANKING"] ?: 0.0)),
            RewriteShellSkillValue("Attack", "attackIcon.png", legacyLevelForXp(experienceByFamily["ATTACK"] ?: 0.0)),
            RewriteShellSkillValue("Watch", "watchIcon.png", legacyLevelForXp(experienceByFamily["WATCH"] ?: 0.0)),
            RewriteShellSkillValue("Scan", "scan.png", legacyLevelForXp(experienceByFamily["SCANNING"] ?: 0.0)),
            RewriteShellSkillValue("Firewall", "firewallIcon.png", legacyLevelForXp(experienceByFamily["FIREWALL"] ?: 0.0)),
            RewriteShellSkillValue("HTTP", "http.png", legacyLevelForXp(experienceByFamily["HTTP"] ?: 0.0)),
            RewriteShellSkillValue("Redirect", "redirect.png", legacyLevelForXp(experienceByFamily["REDIRECT"] ?: 0.0)),
            RewriteShellSkillValue("Repair", "repair.png", 0),
            RewriteShellSkillValue("Total Level", "totallevel.png", totalLevel),
        )
    }

    private fun cpuPercent(currentCpuLoad: Double, cpuMax: Double): Int {
        if (cpuMax <= 0.0) {
            return 0
        }
        return ((currentCpuLoad / cpuMax) * 100.0).toInt().coerceIn(0, 999)
    }
}

fun formatCountdown(seconds: Int): String {
    val clamped = seconds.coerceAtLeast(0)
    val minutes = clamped / 60
    val remainder = clamped % 60
    return "$minutes:${remainder.toString().padStart(2, '0')}"
}

fun legacyLevelForXp(experience: Double): Int {
    var level = 0
    while (level < LEGACY_XP_TABLE.lastIndex && experience > LEGACY_XP_TABLE[level]) {
        level++
    }
    return level + 1
}

private val LEGACY_XP_TABLE: IntArray = IntArray(100).also { table ->
    var xp = 83
    var xpDiff = 83
    for (index in table.indices) {
        table[index] = xp
        xpDiff = (xpDiff + xpDiff / 9.525).toInt()
        xp += xpDiff
    }
}

private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)
private val numberFormatter = DecimalFormat("0.0#")

const val DEFAULT_FRAME_TITLE: String = "Hack Wars"
