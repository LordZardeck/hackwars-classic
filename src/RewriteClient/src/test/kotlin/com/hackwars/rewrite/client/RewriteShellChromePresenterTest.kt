package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.shell.RewriteShellChromePresenter
import com.hackwars.rewrite.client.shell.formatCountdown
import com.hackwars.rewrite.client.shell.legacyLevelForXp
import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import com.hackwars.rewrite.protocol.ClientComputerIdentity
import com.hackwars.rewrite.protocol.ClientEconomyState
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientHardwareState
import com.hackwars.rewrite.protocol.ClientPlayerStatsState
import com.hackwars.rewrite.protocol.ClientRuntimeState
import com.hackwars.rewrite.protocol.ClientWebsiteState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewriteShellChromePresenterTest {
    @Test
    fun decodedShellStateMapsIntoStatsRailValues() {
        val presenter = RewriteShellChromePresenter()
        presenter.updateRoute(RewriteClientRoute.DESKTOP)
        presenter.updateAcceptedPlayerIp("ACCEPTED-IP")
        presenter.updateShellState(
            ClientGameSnapshot(
                identity = ClientComputerIdentity(playerIp = ""),
                economy = ClientEconomyState(
                    pettyCash = 125.5,
                    bankMoney = 88.25,
                    commodities = listOf(1.0, 2.0, 3.0, 4.0, 5.0),
                ),
                hardware = ClientHardwareState(cpuMax = 10.0),
                website = ClientWebsiteState(voteCount = 7),
                stats = ClientPlayerStatsState(
                    experienceByFamily = mapOf(
                        "BANKING" to 84.0,
                        "ATTACK" to 10_000.0,
                        "WATCH" to 500.0,
                        "SCANNING" to 300.0,
                        "FIREWALL" to 600.0,
                        "HTTP" to 700.0,
                        "REDIRECT" to 800.0,
                    ),
                    totalLevel = 42,
                ),
                runtime = ClientRuntimeState(currentCpuLoad = 8.5),
            ),
        )

        val chrome = presenter.currentState()

        assertTrue(chrome.statsVisible)
        assertFalse(chrome.countdownVisible)
        assertEquals("ACCEPTED-IP", chrome.stats.playerIp)
        assertEquals("$125.50", chrome.stats.pettyCashText)
        assertEquals("$88.25", chrome.stats.bankMoneyText)
        assertEquals("0", chrome.stats.hackOMeterText)
        assertEquals("7", chrome.stats.voteOMeterText)
        assertEquals("8.5 / 10.0", chrome.stats.cpuLoadText)
        assertEquals(85, chrome.stats.cpuPercent)
        assertEquals(listOf("1", "2", "3", "4", "5"), chrome.stats.commodities.map { it.valueText })
        assertEquals(legacyLevelForXp(84.0), chrome.stats.skills.first { it.label == "Merchanting" }.level)
        assertEquals(legacyLevelForXp(10_000.0), chrome.stats.skills.first { it.label == "Attack" }.level)
        assertEquals(42, chrome.stats.skills.first { it.label == "Total Level" }.level)
        assertEquals(0, chrome.stats.skills.first { it.label == "Repair" }.level)
        assertEquals("Hack Wars - ACCEPTED-IP", chrome.frameTitle)
    }

    @Test
    fun missingShellStateFallsBackSafely() {
        val presenter = RewriteShellChromePresenter()
        presenter.updateRoute(RewriteClientRoute.DESKTOP)
        presenter.updateAcceptedPlayerIp("192.0.2.10")
        presenter.updateShellState(null)

        val chrome = presenter.currentState()

        assertTrue(chrome.statsVisible)
        assertEquals("192.0.2.10", chrome.stats.playerIp)
        assertEquals("$0.00", chrome.stats.pettyCashText)
        assertEquals("$0.00", chrome.stats.bankMoneyText)
        assertEquals("Hack Wars - 192.0.2.10", chrome.frameTitle)
    }

    @Test
    fun countdownTicksToDisconnectedAndRouteResetClearsIt() {
        val presenter = RewriteShellChromePresenter()
        presenter.updateRoute(RewriteClientRoute.DESKTOP)
        presenter.updateAcceptedPlayerIp("192.0.2.10")
        presenter.updateShellState(
            ClientGameSnapshot(
                identity = ClientComputerIdentity(playerIp = "192.0.2.10"),
                runtime = ClientRuntimeState(countdownSeconds = 2),
            ),
        )

        assertEquals("Server Shutdown in 0:02", presenter.currentState().countdown.text)
        assertEquals("Server Shutdown in 0:02", presenter.currentState().frameTitle)

        presenter.tick()
        assertEquals("Server Shutdown in 0:01", presenter.currentState().countdown.text)

        presenter.tick()
        val disconnected = presenter.currentState()
        assertEquals("Disconnected", disconnected.countdown.text)
        assertEquals("Disconnected", disconnected.frameTitle)

        presenter.updateShellState(
            ClientGameSnapshot(
                identity = ClientComputerIdentity(playerIp = "192.0.2.10"),
                runtime = ClientRuntimeState(countdownSeconds = 0),
            ),
        )
        assertEquals("Disconnected", presenter.currentState().countdown.text)

        presenter.updateRoute(RewriteClientRoute.LOGIN)
        val loginState = presenter.currentState()
        assertFalse(loginState.statsVisible)
        assertFalse(loginState.countdownVisible)
        assertEquals("Hack Wars", loginState.frameTitle)
    }

    @Test
    fun clearedCountdownResetsBackToPlayerTitleBeforeDisconnect() {
        val presenter = RewriteShellChromePresenter()
        presenter.updateRoute(RewriteClientRoute.DESKTOP)
        presenter.updateAcceptedPlayerIp("192.0.2.10")
        presenter.updateShellState(
            ClientGameSnapshot(
                identity = ClientComputerIdentity(playerIp = "192.0.2.10"),
                runtime = ClientRuntimeState(countdownSeconds = 75),
            ),
        )

        presenter.updateShellState(
            ClientGameSnapshot(
                identity = ClientComputerIdentity(playerIp = "192.0.2.10"),
                runtime = ClientRuntimeState(countdownSeconds = 0),
            ),
        )

        val state = presenter.currentState()
        assertFalse(state.countdownVisible)
        assertEquals("Hack Wars - 192.0.2.10", state.frameTitle)
    }

    @Test
    fun countdownFormattingUsesMinuteSecondShape() {
        assertEquals("0:00", formatCountdown(0))
        assertEquals("1:05", formatCountdown(65))
    }
}
