package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.economy.RewriteSegmentedIpInput
import com.hackwars.rewrite.client.economy.deriveBankPortOptions
import com.hackwars.rewrite.client.economy.reconcileSelection
import com.hackwars.rewrite.protocol.ClientEconomyState
import com.hackwars.rewrite.protocol.ClientGameSnapshot
import com.hackwars.rewrite.protocol.ClientInstalledApplication
import com.hackwars.rewrite.protocol.ClientPortState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RewriteBankingWindowsTest {
    @Test
    fun bankPortOptionsFilterAndTruncateLegacyLabels() {
        val options = ClientGameSnapshot(
            economy = ClientEconomyState(defaultBankPort = 7),
            ports = listOf(
                ClientPortState(
                    number = 4,
                    note = "Integration Bank",
                    installedApplication = ClientInstalledApplication(kind = "BANKING"),
                ),
                ClientPortState(
                    number = 6,
                    note = "Disabled",
                    enabled = false,
                    installedApplication = ClientInstalledApplication(kind = "BANKING"),
                ),
                ClientPortState(
                    number = 7,
                    note = "Default",
                    installedApplication = ClientInstalledApplication(kind = "BANKING"),
                ),
                ClientPortState(
                    number = 9,
                    note = "Not Bank",
                    installedApplication = ClientInstalledApplication(kind = "HTTP"),
                ),
            ),
        ).deriveBankPortOptions()

        assertEquals(listOf("4: Integra...", "7: Default"), options.map { it.label })
    }

    @Test
    fun selectionPrefersExistingThenPreferredThenDefaultThenFirstPort() {
        val options = ClientGameSnapshot(
            ports = listOf(
                ClientPortState(
                    number = 4,
                    note = "One",
                    installedApplication = ClientInstalledApplication(kind = "BANKING"),
                ),
                ClientPortState(
                    number = 7,
                    note = "Two",
                    installedApplication = ClientInstalledApplication(kind = "BANKING"),
                ),
            ),
        ).deriveBankPortOptions()

        assertEquals(7, reconcileSelection(options, selectedPortNumber = 7, preferredPortNumber = 4, defaultBankPort = 4))
        assertEquals(4, reconcileSelection(options, selectedPortNumber = 2, preferredPortNumber = 4, defaultBankPort = 7))
        assertEquals(7, reconcileSelection(options, selectedPortNumber = null, preferredPortNumber = 2, defaultBankPort = 7))
        assertEquals(4, reconcileSelection(options, selectedPortNumber = null, preferredPortNumber = null, defaultBankPort = 2))
        assertNull(reconcileSelection(emptyList(), selectedPortNumber = null, preferredPortNumber = 4, defaultBankPort = 7))
    }

    @Test
    fun segmentedIpInputBuildsCompleteIpv4Values() {
        val input = RewriteSegmentedIpInput()

        input.setIp("123.123.1.123")

        assertEquals("123.123.1.123", input.valueOrNull())
        input.setIp("123.123.999")
        assertNull(input.valueOrNull())
    }
}
