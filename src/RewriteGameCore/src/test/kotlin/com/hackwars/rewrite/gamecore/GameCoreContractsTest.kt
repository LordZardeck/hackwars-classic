package com.hackwars.rewrite.gamecore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameCoreContractsTest {
    @Test
    fun emptyComputerStateBuildsTypedDefaults() {
        val stateId = GameStateId("LOCAL-IP")

        val state = ComputerState.empty(
            id = stateId,
            playFabId = "PF-LOCALUSER",
            displayName = "Local User",
        )

        assertEquals(stateId, state.id)
        assertEquals("PF-LOCALUSER", state.identity.playFabId)
        assertEquals("LOCAL-IP", state.identity.playerIp)
        assertEquals("Local User", state.identity.displayName)
        assertTrue(state.ports.isEmpty())
        assertTrue(state.preferences.values.isEmpty())
        assertEquals(0, state.runtime.countdownSeconds)
    }

    @Test
    fun commandRegistryRoutesByWireNameAndCreatesTypedCommands() {
        val networkRepository = InMemoryNetworkDirectoryRepository.defaultWorld()
        val registry = CommandRegistry()
            .register("requestscan") { input ->
                val payload = RewriteGameJson.codec.decodeFromString(
                    deserializer = RequestScanPayload.serializer(),
                    string = input.payloadJson ?: error("Expected request scan payload json."),
                )
                RequestScanCommand(
                    requesterStateId = input.metadata.authenticatedStateId ?: GameStateId(payload.ip),
                    targetStateId = GameStateId(payload.targetIp ?: error("Expected target ip.")),
                )
            }
            .register("changenetwork") { input ->
                val payload = RewriteGameJson.codec.decodeFromString(
                    deserializer = ChangeNetworkPayload.serializer(),
                    string = input.payloadJson ?: error("Expected change network payload json."),
                )
                ChangeNetworkCommand(
                    stateId = input.metadata.authenticatedStateId ?: GameStateId(payload.ip),
                    targetNetworkName = payload.network,
                    networkDirectoryRepository = networkRepository,
                )
            }
            .register("requestsearch") { input ->
                val payload = RewriteGameJson.codec.decodeFromString(
                    deserializer = RequestSearchPayload.serializer(),
                    string = input.payloadJson ?: error("Expected request search payload json."),
                )
                RequestSearchCommand(
                    requesterStateId = input.metadata.authenticatedStateId ?: GameStateId("LOCAL-IP"),
                    query = payload.query.orEmpty(),
                    startIndex = payload.startIndex,
                    searchCatalogRepository = InMemorySearchCatalogRepository(emptyList()),
                )
            }
            .register("fetchwatches") { input ->
                val payload = RewriteGameJson.codec.decodeFromString(
                    deserializer = FetchWatchesPayload.serializer(),
                    string = input.payloadJson ?: error("Expected fetch watches payload json."),
                )
                FetchWatchesCommand(
                    stateId = input.metadata.authenticatedStateId ?: GameStateId(payload.ip),
                )
            }
            .register("installwatch") { input ->
                val payload = RewriteGameJson.codec.decodeFromString(
                    deserializer = InstallWatchPayload.serializer(),
                    string = input.payloadJson ?: error("Expected install watch payload json."),
                )
                InstallWatchCommand(
                    stateId = input.metadata.authenticatedStateId ?: GameStateId(payload.ip),
                    path = payload.path,
                    fileName = payload.name ?: error("Expected watch file name."),
                    typeCode = payload.type ?: error("Expected watch type."),
                    portNumber = payload.port ?: error("Expected watch port."),
                )
            }
            .register("setpreferences") { input ->
                val payload = RewriteGameJson.codec.decodeFromString(
                    deserializer = SetPreferencePayload.serializer(),
                    string = input.payloadJson ?: error("Expected preference payload json."),
                )
                SetPreferenceCommand(
                    stateId = input.targetStateIds.single(),
                    key = payload.key,
                    value = payload.value,
                )
            }
            .register("setftppassword") { input ->
                val payload = RewriteGameJson.codec.decodeFromString(
                    deserializer = SetFtpPasswordPayload.serializer(),
                    string = input.payloadJson ?: error("Expected FTP password payload json."),
                )
                SetFtpPasswordCommand(
                    stateId = input.targetStateIds.single(),
                    password = payload.password,
                    ftpPasswordRepository = InMemoryFtpPasswordRepository(),
                )
            }

        val scan = registry.requireCreate(
            CommandEnvelopeInput(
                commandId = "scan-1",
                commandName = "requestscan",
                targetStateIds = setOf(GameStateId("LOCAL-IP"), GameStateId("TARGET-IP")),
                payloadJson = RewriteGameJson.codec.encodeToString(
                    serializer = RequestScanPayload.serializer(),
                    value = RequestScanPayload(
                        ip = "LOCAL-IP",
                        targetIp = "TARGET-IP",
                    ),
                ),
                expectsResponse = true,
                metadata = CommandMetadata(authenticatedStateId = GameStateId("LOCAL-IP")),
            ),
        )
        val changeNetwork = registry.requireCreate(
            CommandEnvelopeInput(
                commandId = "network-1",
                commandName = "changenetwork",
                targetStateIds = setOf(GameStateId("LOCAL-IP")),
                payloadJson = RewriteGameJson.codec.encodeToString(
                    serializer = ChangeNetworkPayload.serializer(),
                    value = ChangeNetworkPayload(
                        ip = "LOCAL-IP",
                        network = "ProgNet",
                    ),
                ),
                expectsResponse = true,
                metadata = CommandMetadata(authenticatedStateId = GameStateId("LOCAL-IP")),
            ),
        )
        val search = registry.requireCreate(
            CommandEnvelopeInput(
                commandId = "search-1",
                commandName = "requestsearch",
                targetStateIds = emptySet(),
                payloadJson = RewriteGameJson.codec.encodeToString(
                    serializer = RequestSearchPayload.serializer(),
                    value = RequestSearchPayload(
                        query = "alpha beta",
                        startIndex = 0,
                    ),
                ),
                expectsResponse = true,
                metadata = CommandMetadata(authenticatedStateId = GameStateId("LOCAL-IP")),
            ),
        )
        val fetchWatches = registry.requireCreate(
            CommandEnvelopeInput(
                commandId = "watch-1",
                commandName = "fetchwatches",
                targetStateIds = setOf(GameStateId("LOCAL-IP")),
                payloadJson = RewriteGameJson.codec.encodeToString(
                    serializer = FetchWatchesPayload.serializer(),
                    value = FetchWatchesPayload(ip = "LOCAL-IP"),
                ),
                expectsResponse = true,
                metadata = CommandMetadata(authenticatedStateId = GameStateId("LOCAL-IP")),
            ),
        )
        val installWatch = registry.requireCreate(
            CommandEnvelopeInput(
                commandId = "watch-2",
                commandName = "installwatch",
                targetStateIds = setOf(GameStateId("LOCAL-IP")),
                payloadJson = RewriteGameJson.codec.encodeToString(
                    serializer = InstallWatchPayload.serializer(),
                    value = InstallWatchPayload(
                        ip = "LOCAL-IP",
                        path = "/Public",
                        name = "watch.bin",
                        type = WatchKind.PETTY_CASH.legacyCode,
                        port = 6,
                    ),
                ),
                expectsResponse = true,
                metadata = CommandMetadata(authenticatedStateId = GameStateId("LOCAL-IP")),
            ),
        )
        val setPreference = registry.requireCreate(
            CommandEnvelopeInput(
                commandId = "pref-1",
                commandName = "setpreferences",
                targetStateIds = setOf(GameStateId("LOCAL-IP")),
                payloadJson = RewriteGameJson.codec.encodeToString(
                    serializer = SetPreferencePayload.serializer(),
                    value = SetPreferencePayload(
                        key = "show_tutorials",
                        value = "true",
                    ),
                ),
                expectsResponse = true,
                metadata = CommandMetadata(),
            ),
        )
        val setFtpPassword = registry.requireCreate(
            CommandEnvelopeInput(
                commandId = "ftp-pass-1",
                commandName = "setftppassword",
                targetStateIds = setOf(GameStateId("LOCAL-IP")),
                payloadJson = RewriteGameJson.codec.encodeToString(
                    serializer = SetFtpPasswordPayload.serializer(),
                    value = SetFtpPasswordPayload(
                        ip = "LOCAL-IP",
                        password = "letmein",
                    ),
                ),
                expectsResponse = true,
                metadata = CommandMetadata(),
            ),
        )

        assertEquals(
            setOf(
                "requestscan",
                "changenetwork",
                "requestsearch",
                "fetchwatches",
                "installwatch",
                "setpreferences",
                "setftppassword",
            ),
            registry.registeredNames(),
        )
        assertIs<RequestScanCommand>(scan)
        assertIs<ChangeNetworkCommand>(changeNetwork)
        assertIs<RequestSearchCommand>(search)
        assertIs<FetchWatchesCommand>(fetchWatches)
        assertIs<InstallWatchCommand>(installWatch)
        assertIs<SetPreferenceCommand>(setPreference)
        assertIs<SetFtpPasswordCommand>(setFtpPassword)
    }
}
