package com.hackwars.rewrite.clientdev

import com.hackwars.rewrite.clientmodel.RewriteClientRoute
import com.hackwars.rewrite.gamecore.DirectoryListingResponse
import com.hackwars.rewrite.gamecore.RequestDirectoryPayload
import com.hackwars.rewrite.gamecore.RequestSecondaryDirectoryPayload
import com.hackwars.rewrite.gamecore.RequestWebpagePayload
import com.hackwars.rewrite.gamecore.RewriteGameJson
import com.hackwars.rewrite.gamecore.SecondaryDirectoryListingResponse
import com.hackwars.rewrite.gamecore.WebsiteRenderResponse
import com.hackwars.rewrite.protocol.RewriteFrames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RewriteClientDevTest {
    @Test
    fun deterministicLoginBootstrapsDesktopThroughRealInMemoryAdapter() = runTest {
        val runtimeScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val workerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val environment = RewriteClientDevEnvironment(
            clock = { java.time.Instant.ofEpochMilli(testScheduler.currentTime) },
            runtimeScope = runtimeScope,
        )
        val controller = environment.createController(workerScope = workerScope)
        try {
            controller.submitLogin("localuser", "password1234".toCharArray())
            repeat(10) {
                runCurrent()
            }

            assertEquals(RewriteClientRoute.DESKTOP, controller.route())
            val shellState = controller.gameShellState()
            assertNotNull(shellState)
            assertEquals("LOCAL-IP", shellState.id)
            assertEquals("Local Development Site", shellState.website.title)
            assertTrue(shellState.filesystem.filesByPath.isNotEmpty())
            assertTrue(shellState.ports.isNotEmpty())
        } finally {
            controller.shutdown()
            environment.close()
            workerScope.cancel()
            runtimeScope.cancel()
        }
    }

    @Test
    fun seededFixtureSupportsRepresentativeRealCommandRoundTrips() = runTest {
        val runtimeScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val environment = RewriteClientDevEnvironment(
            clock = { java.time.Instant.ofEpochMilli(testScheduler.currentTime) },
            runtimeScope = runtimeScope,
        )
        try {
            val connection = environment.authenticatedConnection()

            connection.send(
                RewriteFrames.command(
                    commandId = "dir-1",
                    commandName = "requestdirectory",
                    payload = RewriteGameJson.encode(
                        RequestDirectoryPayload.serializer(),
                        RequestDirectoryPayload(path = "/Software/Binaries"),
                    ),
                    expectsResponse = true,
                ),
            )
            val directoryResponse = RewriteGameJson.decode(
                DirectoryListingResponse.serializer(),
                connection.awaitFrame().command_response!!.payload.toByteArray(),
            )
            assertTrue(directoryResponse.files.any { it.name == "attack.bin" })

            connection.send(
                RewriteFrames.command(
                    commandId = "web-1",
                    commandName = "requestwebpage",
                    payload = RewriteGameJson.encode(
                        RequestWebpagePayload.serializer(),
                        RequestWebpagePayload(
                            targetIp = "TARGET-IP",
                            sourceIp = "LOCAL-IP",
                        ),
                    ),
                    expectsResponse = true,
                ),
            )
            val websiteResponse = RewriteGameJson.decode(
                WebsiteRenderResponse.serializer(),
                connection.awaitFrame().command_response!!.payload.toByteArray(),
            )
            assertEquals("Target Commerce Hub", websiteResponse.title)
            assertTrue(websiteResponse.storeFiles.any { it.name == "shipping_manifest.bin" })

            connection.send(
                RewriteFrames.command(
                    commandId = "secondary-1",
                    commandName = "requestsecondarydirectory",
                    payload = RewriteGameJson.encode(
                        RequestSecondaryDirectoryPayload.serializer(),
                        RequestSecondaryDirectoryPayload(
                            path = "/Public",
                            targetIp = "TARGET-IP",
                            port = 17,
                        ),
                    ),
                    expectsResponse = true,
                ),
            )
            val secondaryResponse = RewriteGameJson.decode(
                SecondaryDirectoryListingResponse.serializer(),
                connection.awaitFrame().command_response!!.payload.toByteArray(),
            )
            assertEquals("/Public", secondaryResponse.path)
            assertTrue(secondaryResponse.files.any { it.name == "loot.txt" })
            connection.close()
        } finally {
            environment.close()
            runtimeScope.cancel()
        }
    }
}
