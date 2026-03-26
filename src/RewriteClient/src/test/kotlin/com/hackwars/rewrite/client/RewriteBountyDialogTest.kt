package com.hackwars.rewrite.client

import com.hackwars.rewrite.client.economy.allowBountyChooserDirectory
import com.hackwars.rewrite.client.economy.allowBountyChooserFile
import com.hackwars.rewrite.client.economy.rewriteBountyTypeOptions
import com.hackwars.rewrite.protocol.ClientApplicationKind
import com.hackwars.rewrite.protocol.ClientCompiledBinaryMetadata
import com.hackwars.rewrite.protocol.ClientDirectoryEntry
import com.hackwars.rewrite.protocol.ClientStoredFile
import com.hackwars.rewrite.protocol.ClientStoredFileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewriteBountyDialogTest {
    @Test
    fun legacyBountyTypeLabelsStayInServerOrder() {
        assertEquals(
            listOf(
                "Scan",
                "Attack",
                "Install Script",
                "Vote",
                "Change HTTP Target",
                "Destroy Watches",
            ),
            rewriteBountyTypeOptions.map { it.label },
        )
        assertEquals(listOf(0, 1, 2, 3, 4, 5), rewriteBountyTypeOptions.map { it.typeIndex })
    }

    @Test
    fun bountyChooserFiltersLegacyDirectoriesAndCompiledApplicationKinds() {
        assertFalse(allowBountyChooserDirectory(ClientDirectoryEntry(path = "/Store", name = "Store")))
        assertFalse(allowBountyChooserDirectory(ClientDirectoryEntry(path = "/Public", name = "Public")))
        assertTrue(allowBountyChooserDirectory(ClientDirectoryEntry(path = "/Scripts", name = "Scripts")))

        assertTrue(
            allowBountyChooserFile(
                ClientStoredFile(
                    path = "/Scripts/installer.bin",
                    name = "installer.bin",
                    kind = ClientStoredFileKind.APPLICATION_BINARY,
                    compiledBinary = ClientCompiledBinaryMetadata(applicationKind = ClientApplicationKind.ATTACK),
                ),
            ),
        )
        assertTrue(
            allowBountyChooserFile(
                ClientStoredFile(
                    path = "/Scripts/http.bin",
                    name = "http.bin",
                    kind = ClientStoredFileKind.APPLICATION_BINARY,
                    compiledBinary = ClientCompiledBinaryMetadata(applicationKind = ClientApplicationKind.HTTP),
                ),
            ),
        )
        assertFalse(
            allowBountyChooserFile(
                ClientStoredFile(
                    path = "/Scripts/watch.bin",
                    name = "watch.bin",
                    kind = ClientStoredFileKind.APPLICATION_BINARY,
                    compiledBinary = ClientCompiledBinaryMetadata(applicationKind = ClientApplicationKind.WATCH),
                ),
            ),
        )
        assertFalse(
            allowBountyChooserFile(
                ClientStoredFile(
                    path = "/Scripts/readme.txt",
                    name = "readme.txt",
                    kind = ClientStoredFileKind.TEXT,
                ),
            ),
        )
    }
}
