package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.ApplicationKind
import com.hackwars.rewrite.gamecore.ActiveQuestProgress
import com.hackwars.rewrite.gamecore.CompiledBinaryMetadata
import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.EconomyState
import com.hackwars.rewrite.gamecore.EquipmentSlot
import com.hackwars.rewrite.gamecore.FirewallCombatProfile
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.InstalledApplication
import com.hackwars.rewrite.gamecore.InstalledEquipment
import com.hackwars.rewrite.gamecore.InstalledFirewall
import com.hackwars.rewrite.gamecore.InstalledWatch
import com.hackwars.rewrite.gamecore.NetworkDirectoryDefinition
import com.hackwars.rewrite.gamecore.NetworkState
import com.hackwars.rewrite.gamecore.PlayerStatsState
import com.hackwars.rewrite.gamecore.PortState
import com.hackwars.rewrite.gamecore.ProgramScriptBundle
import com.hackwars.rewrite.gamecore.ProgramScriptSlot
import com.hackwars.rewrite.gamecore.QuestState
import com.hackwars.rewrite.gamecore.ROOT_NETWORK_NAME
import com.hackwars.rewrite.gamecore.SaveFileMetadata
import com.hackwars.rewrite.gamecore.ScriptFamily
import com.hackwars.rewrite.gamecore.StoredFile
import com.hackwars.rewrite.gamecore.StoredFileKind
import com.hackwars.rewrite.gamecore.WatchKind
import com.hackwars.rewrite.gamecore.WatchManagerState
import com.hackwars.rewrite.gamecore.WebsiteState
import com.hackwars.rewrite.gamecore.FirewallKind
import com.hackwars.rewrite.gamecore.buildFilePath
import com.hackwars.rewrite.gamecore.ensureDirectory
import com.hackwars.rewrite.gamecore.saveFile
import com.hackwars.rewrite.hackscript.BooleanHookValue
import com.hackwars.rewrite.hackscript.FloatHookValue
import com.hackwars.rewrite.hackscript.HookValue
import com.hackwars.rewrite.hackscript.IntHookValue
import com.hackwars.rewrite.hackscript.StringHookValue
import java.sql.Connection
import java.sql.Timestamp
import java.util.Base64

class JdbcRewriteSeedSink(
    private val connectionFactory: () -> Connection,
    private val serializer: ComputerStateSerializer = ComputerStateSerializer(),
) : RewriteSeedSink {
    override suspend fun write(batch: RewriteSeedBatch) {
        connectionFactory().use { connection ->
            connection.autoCommit = false
            try {
                insertImportBatch(connection, batch)
                when (val payload = batch.seedPayload) {
                    is SeedPlayerAccount -> upsertPlayerAccount(connection, payload)
                    is PersistedSessionTicket -> upsertSessionTicket(connection, payload)
                    is PersistedServiceSession -> upsertServiceSession(connection, payload)
                    is SeedComputerState -> upsertComputerState(connection, payload)
                    is SeedWorldDirectory -> upsertWorldDirectory(connection, payload)
                    is SeedInventorySnapshot -> applyInventorySnapshot(connection, payload)
                    is SeedChatSocialSnapshot -> applyChatSocialSnapshot(connection, payload)
                }
                connection.commit()
            } catch (exception: Throwable) {
                connection.rollback()
                throw exception
            }
        }
    }

    private fun insertImportBatch(
        connection: Connection,
        batch: RewriteSeedBatch,
    ) {
        connection.prepareStatement(
            """
            insert into rewrite_import_batch(batch_id, source_kind, source_location, seed_payload, created_at)
            values (?, ?, ?, cast(? as jsonb), ?)
            on conflict (batch_id) do update
            set source_kind = excluded.source_kind,
                source_location = excluded.source_location,
                seed_payload = excluded.seed_payload,
                created_at = excluded.created_at
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, batch.batchId)
            statement.setString(2, batch.source.sourceKind)
            statement.setString(3, batch.source.sourceLocation)
            statement.setString(4, seedPayloadJson(batch.seedPayload))
            statement.setTimestamp(5, Timestamp.from(batch.createdAt))
            statement.executeUpdate()
        }
    }

    private fun upsertPlayerAccount(
        connection: Connection,
        payload: SeedPlayerAccount,
    ) {
        connection.prepareStatement(
            """
            insert into rewrite_player_account(player_id, playfab_id, player_ip, account_payload)
            values (?, ?, ?, cast(? as jsonb))
            on conflict (player_id) do update
            set playfab_id = excluded.playfab_id,
                player_ip = excluded.player_ip,
                account_payload = excluded.account_payload
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, payload.playerId)
            statement.setString(2, payload.playFabId)
            statement.setString(3, payload.playerIp)
            statement.setString(
                4,
                """
                {"playerId":"${payload.playerId}","playFabId":"${payload.playFabId}","playerIp":"${payload.playerIp}"}
                """.trimIndent(),
            )
            statement.executeUpdate()
        }
    }

    private fun upsertSessionTicket(
        connection: Connection,
        payload: PersistedSessionTicket,
    ) {
        connection.prepareStatement(
            """
            insert into rewrite_session_ticket(
                session_ticket,
                player_id,
                playfab_id,
                player_ip,
                issued_at,
                expires_at,
                ticket_payload
            )
            values (?, ?, ?, ?, ?, ?, cast(? as jsonb))
            on conflict (session_ticket) do update
            set player_id = excluded.player_id,
                playfab_id = excluded.playfab_id,
                player_ip = excluded.player_ip,
                issued_at = excluded.issued_at,
                expires_at = excluded.expires_at,
                ticket_payload = excluded.ticket_payload
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, payload.sessionTicket)
            statement.setString(2, payload.playerId)
            statement.setString(3, payload.playFabId)
            statement.setString(4, payload.playerIp)
            statement.setTimestamp(5, Timestamp.from(payload.issuedAt))
            statement.setTimestamp(6, payload.expiresAt?.let(Timestamp::from))
            statement.setString(7, payload.ticketPayload)
            statement.executeUpdate()
        }
    }

    private fun upsertServiceSession(
        connection: Connection,
        payload: PersistedServiceSession,
    ) {
        connection.prepareStatement(
            """
            insert into rewrite_service_session(
                service_session_id,
                service_kind,
                connection_id,
                player_id,
                playfab_id,
                player_ip,
                session_ticket,
                client_build,
                heartbeat_interval_millis,
                authenticated_at,
                last_seen_at,
                closed_at,
                session_payload
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
            on conflict (service_kind, connection_id) do update
            set service_session_id = excluded.service_session_id,
                player_id = excluded.player_id,
                playfab_id = excluded.playfab_id,
                player_ip = excluded.player_ip,
                session_ticket = excluded.session_ticket,
                client_build = excluded.client_build,
                heartbeat_interval_millis = excluded.heartbeat_interval_millis,
                authenticated_at = excluded.authenticated_at,
                last_seen_at = excluded.last_seen_at,
                closed_at = excluded.closed_at,
                session_payload = excluded.session_payload
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, payload.serviceSessionId)
            statement.setString(2, payload.serviceKind.name)
            statement.setString(3, payload.connectionId)
            statement.setString(4, payload.playerId)
            statement.setString(5, payload.playFabId)
            statement.setString(6, payload.playerIp)
            statement.setString(7, payload.sessionTicket)
            statement.setString(8, payload.clientBuild)
            statement.setLong(9, payload.heartbeatIntervalMillis)
            statement.setTimestamp(10, Timestamp.from(payload.authenticatedAt))
            statement.setTimestamp(11, Timestamp.from(payload.lastSeenAt))
            statement.setTimestamp(12, payload.closedAt?.let(Timestamp::from))
            statement.setString(13, payload.sessionPayload)
            statement.executeUpdate()
        }
    }

    private fun upsertComputerState(
        connection: Connection,
        payload: SeedComputerState,
    ) {
        val stateId = GameStateId(payload.computerId)
        val state = ComputerState.empty(
            id = stateId,
            playerIp = payload.ipAddress,
            displayName = payload.playerId,
            isNpc = payload.isNpc,
        )
        connection.prepareStatement(
            """
            insert into rewrite_computer_state(computer_id, player_id, ip_address, state_payload)
            values (?, ?, ?, cast(? as jsonb))
            on conflict (computer_id) do update
            set player_id = excluded.player_id,
                ip_address = excluded.ip_address,
                state_payload = excluded.state_payload
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, payload.computerId)
            statement.setString(2, payload.playerId)
            statement.setString(3, payload.ipAddress)
            statement.setString(4, serializer.encodeStateJson(state))
            statement.executeUpdate()
        }
    }

    private fun upsertWorldDirectory(
        connection: Connection,
        payload: SeedWorldDirectory,
    ) {
        payload.networks.forEach { network ->
            connection.prepareStatement(
                """
                insert into rewrite_network_directory(network_name, store_state_id)
                values (?, ?)
                on conflict (network_name) do update
                set store_state_id = excluded.store_state_id
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, network.name)
                statement.setString(2, network.storeStateId)
                statement.executeUpdate()
            }
        }

        payload.networks.forEach { network ->
            connection.prepareStatement(
                "delete from rewrite_network_link where from_network_name = ?",
            ).use { statement ->
                statement.setString(1, network.name)
                statement.executeUpdate()
            }
            network.attachedNetworks.forEach { link ->
                connection.prepareStatement(
                    """
                    insert into rewrite_network_link(from_network_name, to_network_name, entrance_message, failure_message)
                    values (?, ?, ?, ?)
                    on conflict (from_network_name, to_network_name) do update
                    set entrance_message = excluded.entrance_message,
                        failure_message = excluded.failure_message
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, network.name)
                    statement.setString(2, link.targetNetworkName)
                    statement.setString(3, link.entranceMessage)
                    statement.setString(4, link.failureMessage)
                    statement.executeUpdate()
                }
            }

            connection.prepareStatement(
                "delete from rewrite_network_npc where network_name = ?",
            ).use { statement ->
                statement.setString(1, network.name)
                statement.executeUpdate()
            }
            network.npcs.forEachIndexed { index, npc ->
                connection.prepareStatement(
                    """
                    insert into rewrite_network_npc(
                        network_name,
                        state_id,
                        display_name,
                        title,
                        category,
                        commodity,
                        sort_order
                    )
                    values (?, ?, ?, ?, ?, ?, ?)
                    on conflict (network_name, state_id) do update
                    set display_name = excluded.display_name,
                        title = excluded.title,
                        category = excluded.category,
                        commodity = excluded.commodity,
                        sort_order = excluded.sort_order
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, network.name)
                    statement.setString(2, npc.stateId)
                    statement.setString(3, npc.displayName)
                    statement.setString(4, npc.title)
                    statement.setString(5, npc.category.name)
                    statement.setString(6, npc.commodity)
                    statement.setInt(7, index)
                    statement.executeUpdate()
                }
            }
        }
    }

    private fun applyInventorySnapshot(
        connection: Connection,
        payload: SeedInventorySnapshot,
    ) {
        val current = connection.prepareStatement(
            """
            select state_payload::text
            from rewrite_computer_state
            where computer_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, payload.computerId)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) {
                    serializer.decodeStateJson(resultSet.getString(1))
                } else {
                    ComputerState.empty(GameStateId(payload.computerId), playerIp = payload.computerId)
                }
            }
        }
        var filesystem = current.filesystem
            .ensureDirectory("/migration")
            .ensureDirectory("/Public")
            .ensureDirectory("/Store")
            .saveFile(
                StoredFile(
                    path = buildFilePath("/", "readme.txt"),
                    name = "readme.txt",
                    kind = StoredFileKind.TEXT,
                    contents = "Rewrite importer seeded root file",
                    description = "Seeded root note",
                ),
            )
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Store", "catalog.txt"),
                    name = "catalog.txt",
                    kind = StoredFileKind.TEXT,
                    contents = "Seeded store catalog",
                    description = "Seeded store file",
                ),
            )
            .saveFile(
                StoredFile(
                    path = buildFilePath("/Public", "bank.bin"),
                    name = "bank.bin",
                    kind = StoredFileKind.APPLICATION_BINARY,
                    contents = "compiled banking payload",
                    description = "Seeded installable banking application",
                    maker = "rewrite-import",
                    compileCost = 100.0,
                    quantity = 1,
                    compiledBinary = CompiledBinaryMetadata(
                        applicationKind = ApplicationKind.BANKING,
                        bankingApplication = true,
                        outputName = "bank.bin",
                    ),
                ),
            )
        if (payload.enableHttp) {
            val enterScript = if (current.identity.isNpc) {
                """
                int main() {
                    triggerWatchRemote(2, "TARGET-IP", "reason", "npc-hook");
                    return 0;
                }
                """.trimIndent()
            } else {
                """
                int main() {
                    logMessage("website visited");
                    popUp("Welcome visitor");
                    replaceContent("first", getVisitorIP());
                    return 0;
                }
                """.trimIndent()
            }
            val httpBundle = ProgramScriptBundle(
                family = ScriptFamily.HTTP,
                scriptsBySlot = linkedMapOf(
                    ProgramScriptSlot.ENTER to enterScript,
                    ProgramScriptSlot.EXIT to "int main() { return 0; }",
                    ProgramScriptSlot.SUBMIT to "int main() { hideStore(); return 0; }",
                ),
            )
            filesystem = filesystem
                .saveFile(
                    StoredFile(
                        path = buildFilePath("/Public", "http"),
                        name = "http",
                        kind = StoredFileKind.SCRIPT_SOURCE,
                        contents = "seeded http script",
                        description = "Seeded editable HTTP source",
                        maker = "rewrite-import",
                        compileCost = 80.0,
                        quantity = 1,
                        compiledBinary = CompiledBinaryMetadata(
                            scriptFamily = ScriptFamily.HTTP,
                            applicationKind = ApplicationKind.HTTP,
                            outputName = "http.bin",
                        ),
                        scriptBundle = httpBundle,
                    ),
                )
                .saveFile(
                    StoredFile(
                        path = buildFilePath("/Public", "http.bin"),
                        name = "http.bin",
                        kind = StoredFileKind.APPLICATION_BINARY,
                        contents = "seeded http script",
                        description = "Seeded installable HTTP application",
                        maker = "rewrite-import",
                        compileCost = 80.0,
                        quantity = 1,
                        compiledBinary = CompiledBinaryMetadata(
                            scriptFamily = ScriptFamily.HTTP,
                            applicationKind = ApplicationKind.HTTP,
                            outputName = "http.bin",
                        ),
                        scriptBundle = httpBundle,
                    ),
                )
        }
        if (payload.enableWatchBinary) {
            filesystem = filesystem.saveFile(
                StoredFile(
                    path = buildFilePath("/Public", "watch.bin"),
                    name = "watch.bin",
                    kind = StoredFileKind.APPLICATION_BINARY,
                    contents = "seeded watch script",
                    description = "Seeded installable watch binary",
                    maker = "rewrite-import",
                    compileCost = 60.0,
                    cpuCost = payload.seedWatchCpuCost,
                    quantity = 1,
                    compiledBinary = CompiledBinaryMetadata(
                        scriptFamily = ScriptFamily.WATCH,
                        applicationKind = ApplicationKind.WATCH,
                        outputName = "watch.bin",
                    ),
                ),
            )
        }
        payload.notes.forEachIndexed { index, note ->
            filesystem = filesystem.saveFile(
                StoredFile(
                    path = buildFilePath("/migration", "note-${index + 1}.txt"),
                    name = "note-${index + 1}.txt",
                    kind = StoredFileKind.NOTE,
                    contents = note,
                    description = "Migrated importer note",
                ),
            )
        }
        payload.seedSaveFileName?.takeUnless { it.isBlank() }?.let { baseName ->
            val saveValues = linkedMapOf<String, HookValue>(
                "quest" to StringHookValue("migration"),
                "attempt" to IntHookValue(1),
                "verified" to BooleanHookValue(true),
                "score" to FloatHookValue(3.5),
            )
            filesystem = filesystem.saveFile(
                StoredFile(
                    path = buildFilePath("/", "$baseName.save"),
                    name = "$baseName.save",
                    kind = StoredFileKind.SAVE_DATA,
                    contents = serializeSeedSaveRows(saveValues),
                    description = "A save file for $baseName.",
                    maker = baseName,
                    saveMetadata = SaveFileMetadata(valuesByKey = saveValues),
                ),
            )
        }
        val ports = buildList {
            if (payload.enableBanking) {
                add(
                    PortState(
                        number = 6,
                        type = "banking",
                        enabled = true,
                        defaultPort = true,
                        installedApplication = InstalledApplication(
                            name = "bank.bin",
                            kind = ApplicationKind.BANKING,
                            binaryPath = "/Public/bank.bin",
                            banking = true,
                        ),
                    ),
                )
            }
            if (payload.enableFtp) {
                add(
                    PortState(
                        number = 21,
                        type = "ftp",
                        enabled = true,
                        defaultPort = true,
                        installedApplication = InstalledApplication(
                            name = "ftp.bin",
                            kind = ApplicationKind.FTP,
                            binaryPath = "/system/ftp.bin",
                        ),
                    ),
                )
            }
            if (payload.enableHttp) {
                add(
                    PortState(
                        number = 80,
                        type = "http",
                        enabled = true,
                        defaultPort = true,
                        maxCpuCost = 8.0,
                        installedApplication = InstalledApplication(
                            name = "http.bin",
                            kind = ApplicationKind.HTTP,
                            binaryPath = "/system/http.bin",
                            scriptBundle = ProgramScriptBundle(
                                family = ScriptFamily.HTTP,
                                scriptsBySlot = linkedMapOf(
                                    ProgramScriptSlot.ENTER to "int main() { replaceContent(\"first\", getVisitorIP()); return 0; }",
                                    ProgramScriptSlot.EXIT to "int main() { return 0; }",
                                    ProgramScriptSlot.SUBMIT to "int main() { hideStore(); return 0; }",
                                ),
                            ),
                        ),
                        installedFirewall = InstalledFirewall(
                            name = "seed-http-wall.bin",
                            kind = FirewallKind.BASIC,
                            maker = "rewrite-import",
                            binaryPath = "/system/seed-http-wall.bin",
                            strength = 12,
                            cpuCost = 2.0,
                            combatProfile = FirewallCombatProfile(
                                bankDamageModifier = 0.9,
                                ftpDamageModifier = 0.95,
                                httpDamageModifier = 0.8,
                                attackDamageModifier = 0.85,
                                redirectDamageModifier = 0.9,
                                attackBackDamage = 1.5,
                            ),
                        ),
                    ),
                )
            }
        }
        val networkDefinition = JdbcNetworkDirectoryRepository.loadNetwork(connection, payload.currentNetworkName)
            ?: JdbcNetworkDirectoryRepository.loadNetwork(connection, ROOT_NETWORK_NAME)
            ?: NetworkDirectoryDefinition(name = ROOT_NETWORK_NAME)
        val equipmentSlots = current.hardware.equipmentSlots.toMutableMap().apply {
            if (payload.watchCapacityBoost > 0) {
                put(
                    EquipmentSlot.PCI,
                    InstalledEquipment(
                        slot = EquipmentSlot.PCI,
                        name = "watch-capacity-card.bin",
                        maker = "rewrite-import",
                        binaryPath = "/system/watch-capacity-card.bin",
                        watchCapacityBoost = payload.watchCapacityBoost,
                    ),
                )
            }
            if (payload.freezeImmune) {
                val currentAgp = this[EquipmentSlot.AGP]
                put(
                    EquipmentSlot.AGP,
                    (currentAgp ?: InstalledEquipment(
                        slot = EquipmentSlot.AGP,
                        name = "freeze-shield.bin",
                        maker = "rewrite-import",
                        binaryPath = "/system/freeze-shield.bin",
                    )).copy(freezeImmune = true),
                )
            }
            if (payload.destroyWatchesImmune) {
                val currentCpu = this[EquipmentSlot.CPU]
                put(
                    EquipmentSlot.CPU,
                    (currentCpu ?: InstalledEquipment(
                        slot = EquipmentSlot.CPU,
                        name = "watch-guard.bin",
                        maker = "rewrite-import",
                        binaryPath = "/system/watch-guard.bin",
                    )).copy(destroyWatchesImmune = true),
                )
            }
        }
        val enabledWatchCount = payload.seedEnabledWatchCount.coerceIn(0, payload.seedInstalledWatchCount)
        val seededWatches = List(payload.seedInstalledWatchCount) { index ->
            InstalledWatch(
                kind = WatchKind.PETTY_CASH,
                enabled = index < enabledWatchCount,
                note = "seeded-watch-${index + 1}",
                cpuCost = payload.seedWatchCpuCost,
                quantityThreshold = 0.0,
                baselineQuantity = payload.pettyCash,
                installPort = 6,
                searchFirewallType = 0,
                observedPorts = listOf(6),
                contents = "seeded watch script",
                scriptBundle = ProgramScriptBundle(
                    family = ScriptFamily.WATCH,
                    scriptsBySlot = linkedMapOf(
                        ProgramScriptSlot.FIRE to """
                            int main() {
                                logMessage("seeded-watch-${index + 1}");
                                return 0;
                            }
                        """.trimIndent(),
                    ),
                ),
                compiledBinary = CompiledBinaryMetadata(
                    scriptFamily = ScriptFamily.WATCH,
                    applicationKind = ApplicationKind.WATCH,
                    outputName = "watch.bin",
                ),
            )
        }
        val updated = current.copy(
            identity = current.identity.copy(
                lastLoginAtEpochMillis = payload.lastLoginAtEpochMillis,
            ),
            economy = current.economy.copy(
                pettyCash = payload.pettyCash,
                bankMoney = payload.bankMoney,
                commodities = payload.commodities.normalizedCommodityList(),
                defaultBankPort = if (payload.enableBanking) 6 else null,
                commodityRespawn = payload.commodityRespawn.normalizedCommodityList(),
            ),
            filesystem = filesystem,
            ports = ports,
            network = NetworkState(
                currentNetworkName = networkDefinition.name,
                storeStateId = networkDefinition.storeStateId,
                allowedNetworks = payload.allowedNetworks.toSet(),
                lastNetworkSwitchAtEpochMillis = payload.lastNetworkSwitchAtEpochMillis,
                regularNpcs = networkDefinition.regularNpcs,
                questNpcs = networkDefinition.questNpcs,
                miningNpcs = networkDefinition.miningNpcs,
                storeNpcs = networkDefinition.storeNpcs,
            ),
            hardware = current.hardware.copy(
                cpuMax = payload.cpuMax,
                memoryType = payload.memoryType,
                equipmentSlots = equipmentSlots,
            ),
            watches = WatchManagerState(watches = seededWatches),
            quests = QuestState(
                activeQuestsById = payload.activeQuestLabelsById.mapValues { (questId, label) ->
                    ActiveQuestProgress(
                        questId = questId,
                        label = label,
                    )
                },
                completedQuestIds = current.quests.completedQuestIds,
                lastClueDataByIp = current.quests.lastClueDataByIp,
            ),
            website = WebsiteState(
                title = payload.websiteTitle,
                body = payload.websiteBody,
                votesAvailable = payload.votesAvailable,
                voteCount = payload.voteCount,
                storeRevenueTargetStateId = current.website.storeRevenueTargetStateId,
            ),
            stats = PlayerStatsState(
                experienceByFamily = buildMap {
                    putAll(current.stats.experienceByFamily)
                    put(ScriptFamily.SCANNING, payload.scanningExperience)
                    put(ScriptFamily.FIREWALL, payload.firewallExperience)
                },
                totalLevel = payload.totalLevel,
                noobProtectionLevel = payload.noobProtectionLevel,
            ),
            runtime = current.runtime.copy(currentCpuLoad = payload.currentCpuLoad),
        )
        connection.prepareStatement(
            """
            update rewrite_computer_state
            set state_payload = cast(? as jsonb)
            where computer_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, serializer.encodeStateJson(updated))
            statement.setString(2, payload.computerId)
            statement.executeUpdate()
        }
        upsertWebsiteProjection(connection, updated)
    }

    private fun upsertWebsiteProjection(
        connection: Connection,
        state: ComputerState,
    ) {
        val canonicalAddress = state.identity.playerIp.ifBlank { state.id.value }.lowercase()
        val revenueTargetStateId = state.website.storeRevenueTargetStateId?.value
        val websitePayload =
            """
            {"stateId":"${state.id.value}","canonicalAddress":"$canonicalAddress","title":"${state.website.title}","bodyHtml":"${state.website.body}","voteCount":${state.website.voteCount},"votesAvailable":${state.website.votesAvailable},"storeRevenueTargetStateId":"${revenueTargetStateId.orEmpty()}"}
            """.trimIndent()
        connection.prepareStatement(
            """
            insert into rewrite_website_projection(
                state_id,
                canonical_address,
                title,
                body_html,
                vote_count,
                votes_available,
                store_revenue_target_state_id,
                website_payload
            )
            values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
            on conflict (state_id) do update
            set canonical_address = excluded.canonical_address,
                title = excluded.title,
                body_html = excluded.body_html,
                vote_count = excluded.vote_count,
                votes_available = excluded.votes_available,
                store_revenue_target_state_id = excluded.store_revenue_target_state_id,
                website_payload = excluded.website_payload
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, state.id.value)
            statement.setString(2, canonicalAddress)
            statement.setString(3, state.website.title)
            statement.setString(4, state.website.body)
            statement.setInt(5, state.website.voteCount)
            statement.setInt(6, state.website.votesAvailable)
            statement.setString(7, revenueTargetStateId)
            statement.setString(8, websitePayload)
            statement.executeUpdate()
        }
    }

    private fun applyChatSocialSnapshot(
        connection: Connection,
        payload: SeedChatSocialSnapshot,
    ) {
        payload.channels.forEach { channel ->
            connection.prepareStatement(
                """
                insert into rewrite_chat_channel(
                    channel_id,
                    display_name,
                    topic,
                    owner_player_id,
                    private_channel,
                    created_at,
                    channel_payload
                )
                values (?, ?, ?, ?, ?, ?, cast(? as jsonb))
                on conflict (channel_id) do update
                set display_name = excluded.display_name,
                    topic = excluded.topic,
                    owner_player_id = excluded.owner_player_id,
                    private_channel = excluded.private_channel,
                    created_at = excluded.created_at,
                    channel_payload = excluded.channel_payload
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, channel.channelId)
                statement.setString(2, channel.displayName)
                statement.setString(3, channel.topic)
                statement.setString(4, channel.ownerPlayerId)
                statement.setBoolean(5, channel.privateChannel)
                statement.setTimestamp(6, Timestamp.from(channel.createdAt))
                statement.setString(7, channel.channelPayload)
                statement.executeUpdate()
            }
        }
        payload.memberships.forEach { membership ->
            connection.prepareStatement(
                """
                insert into rewrite_chat_channel_member(
                    channel_id,
                    player_id,
                    role,
                    joined_at,
                    membership_payload
                )
                values (?, ?, ?, ?, cast(? as jsonb))
                on conflict (channel_id, player_id) do update
                set role = excluded.role,
                    joined_at = excluded.joined_at,
                    membership_payload = excluded.membership_payload
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, membership.channelId)
                statement.setString(2, membership.playerId)
                statement.setString(3, membership.role.name)
                statement.setTimestamp(4, Timestamp.from(membership.joinedAt))
                statement.setString(5, membership.membershipPayload)
                statement.executeUpdate()
            }
        }
        payload.messages.forEach { message ->
            connection.prepareStatement(
                """
                insert into rewrite_chat_message(
                    message_id,
                    message_kind,
                    channel_id,
                    sender_player_id,
                    recipient_player_id,
                    event_type,
                    payload,
                    created_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, message.messageId)
                statement.setString(2, message.messageKind.name)
                statement.setString(3, message.channelId)
                statement.setString(4, message.senderPlayerId)
                statement.setString(5, message.recipientPlayerId)
                statement.setString(6, message.eventType)
                statement.setBytes(7, message.payload)
                statement.setTimestamp(8, Timestamp.from(message.createdAt))
                statement.executeUpdate()
            }
        }
        payload.relations.forEach { relation ->
            connection.prepareStatement(
                """
                insert into rewrite_chat_relation(
                    player_id,
                    target_player_id,
                    relation_kind,
                    created_at,
                    relation_payload
                )
                values (?, ?, ?, ?, cast(? as jsonb))
                on conflict (player_id, target_player_id, relation_kind) do update
                set created_at = excluded.created_at,
                    relation_payload = excluded.relation_payload
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, relation.playerId)
                statement.setString(2, relation.targetPlayerId)
                statement.setString(3, relation.relationKind.name)
                statement.setTimestamp(4, Timestamp.from(relation.createdAt))
                statement.setString(5, relation.relationPayload)
                statement.executeUpdate()
            }
        }
        payload.channelMutes.forEach { mute ->
            connection.prepareStatement(
                """
                insert into rewrite_chat_channel_mute(
                    player_id,
                    channel_id,
                    muted_player_id,
                    created_at,
                    mute_payload
                )
                values (?, ?, ?, ?, cast(? as jsonb))
                on conflict (player_id, channel_id, muted_player_id) do update
                set created_at = excluded.created_at,
                    mute_payload = excluded.mute_payload
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, mute.playerId)
                statement.setString(2, mute.channelId)
                statement.setString(3, mute.mutedPlayerId)
                statement.setTimestamp(4, Timestamp.from(mute.createdAt))
                statement.setString(5, mute.mutePayload)
                statement.executeUpdate()
            }
        }
        payload.presence.forEach { presence ->
            connection.prepareStatement(
                """
                insert into rewrite_chat_presence(
                    connection_id,
                    player_id,
                    online_at,
                    last_seen_at,
                    offline_at,
                    presence_payload
                )
                values (?, ?, ?, ?, ?, cast(? as jsonb))
                on conflict (connection_id) do update
                set player_id = excluded.player_id,
                    online_at = excluded.online_at,
                    last_seen_at = excluded.last_seen_at,
                    offline_at = excluded.offline_at,
                    presence_payload = excluded.presence_payload
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, presence.connectionId)
                statement.setString(2, presence.playerId)
                statement.setTimestamp(3, Timestamp.from(presence.onlineAt))
                statement.setTimestamp(4, Timestamp.from(presence.lastSeenAt))
                statement.setTimestamp(5, presence.offlineAt?.let(Timestamp::from))
                statement.setString(6, presence.presencePayload)
                statement.executeUpdate()
            }
        }
    }

    private fun seedPayloadJson(payload: SeedPayload): String {
        return when (payload) {
            is SeedPlayerAccount -> """{"type":"player","playerId":"${payload.playerId}","playFabId":"${payload.playFabId}","playerIp":"${payload.playerIp}"}"""
            is PersistedSessionTicket -> """{"type":"session-ticket","sessionTicket":"${payload.sessionTicket}","playerId":"${payload.playerId}","playFabId":"${payload.playFabId}","playerIp":"${payload.playerIp}"}"""
            is PersistedServiceSession -> """{"type":"service-session","serviceSessionId":"${payload.serviceSessionId}","serviceKind":"${payload.serviceKind.name}","connectionId":"${payload.connectionId}","playerId":"${payload.playerId}"}"""
            is SeedComputerState -> """{"type":"computer","computerId":"${payload.computerId}","playerId":"${payload.playerId}","ipAddress":"${payload.ipAddress}"}"""
            is SeedWorldDirectory -> {
                val networksJson = payload.networks.joinToString(prefix = "[", postfix = "]") { network ->
                    val attachedJson = network.attachedNetworks.joinToString(prefix = "[", postfix = "]") { link ->
                        """{"targetNetworkName":"${link.targetNetworkName}","entranceMessage":"${link.entranceMessage}","failureMessage":"${link.failureMessage}"}"""
                    }
                    val npcsJson = network.npcs.joinToString(prefix = "[", postfix = "]") { npc ->
                        """{"stateId":"${npc.stateId}","displayName":"${npc.displayName}","title":"${npc.title}","category":"${npc.category.name}","commodity":"${npc.commodity.orEmpty()}"}"""
                    }
                    """{"name":"${network.name}","storeStateId":"${network.storeStateId.orEmpty()}","attachedNetworks":$attachedJson,"npcs":$npcsJson}"""
                }
                """{"type":"world","networks":$networksJson}"""
            }
            is SeedInventorySnapshot -> {
                val notesJson = payload.notes.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                val commoditiesJson = payload.commodities.normalizedCommodityList().joinToString(prefix = "[", postfix = "]")
                val commodityRespawnJson = payload.commodityRespawn.normalizedCommodityList().joinToString(prefix = "[", postfix = "]")
                val allowedNetworksJson = payload.allowedNetworks.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                val activeQuestsJson = payload.activeQuestLabelsById.entries.joinToString(prefix = "{", postfix = "}") {
                    "\"${it.key}\":\"${it.value}\""
                }
                """{"type":"inventory","computerId":"${payload.computerId}","notes":$notesJson,"websiteTitle":"${payload.websiteTitle}","websiteBody":"${payload.websiteBody}","lastLoginAtEpochMillis":${payload.lastLoginAtEpochMillis ?: "null"},"votesAvailable":${payload.votesAvailable},"voteCount":${payload.voteCount},"totalLevel":${payload.totalLevel},"noobProtectionLevel":${payload.noobProtectionLevel},"pettyCash":${payload.pettyCash},"bankMoney":${payload.bankMoney},"commodities":$commoditiesJson,"commodityRespawn":$commodityRespawnJson,"currentNetworkName":"${payload.currentNetworkName}","allowedNetworks":$allowedNetworksJson,"lastNetworkSwitchAtEpochMillis":${payload.lastNetworkSwitchAtEpochMillis},"scanningExperience":${payload.scanningExperience},"firewallExperience":${payload.firewallExperience},"currentCpuLoad":${payload.currentCpuLoad},"cpuMax":${payload.cpuMax},"memoryType":${payload.memoryType},"watchCapacityBoost":${payload.watchCapacityBoost},"freezeImmune":${payload.freezeImmune},"destroyWatchesImmune":${payload.destroyWatchesImmune},"activeQuestLabelsById":$activeQuestsJson,"seedSaveFileName":"${payload.seedSaveFileName.orEmpty()}","enableBanking":${payload.enableBanking},"enableFtp":${payload.enableFtp},"enableHttp":${payload.enableHttp},"enableWatchBinary":${payload.enableWatchBinary},"seedInstalledWatchCount":${payload.seedInstalledWatchCount},"seedEnabledWatchCount":${payload.seedEnabledWatchCount},"seedWatchCpuCost":${payload.seedWatchCpuCost}}"""
            }
            is SeedChatSocialSnapshot -> {
                val channelsJson = payload.channels.joinToString(prefix = "[", postfix = "]") { channel ->
                    """{"channelId":"${channel.channelId}","displayName":"${channel.displayName}","ownerPlayerId":"${channel.ownerPlayerId}"}"""
                }
                val messagesJson = payload.messages.joinToString(prefix = "[", postfix = "]") { message ->
                    val encodedPayload = Base64.getEncoder().encodeToString(message.payload)
                    """{"messageId":"${message.messageId}","messageKind":"${message.messageKind.name}","eventType":"${message.eventType}","payloadBase64":"$encodedPayload"}"""
                }
                """{"type":"chat-social","channels":$channelsJson,"memberships":${payload.memberships.size},"messages":$messagesJson,"relations":${payload.relations.size},"channelMutes":${payload.channelMutes.size},"presence":${payload.presence.size}}"""
            }
        }
    }

    private fun List<Double>.normalizedCommodityList(): List<Double> {
        return List(5) { index -> getOrNull(index) ?: 0.0 }
    }

    private fun serializeSeedSaveRows(valuesByKey: Map<String, HookValue>): String {
        return buildString {
            valuesByKey.forEach { (key, value) ->
                append(key)
                append('\t')
                append(value.seedSaveType())
                append('\t')
                append(value.seedSaveValue())
                append('\n')
            }
        }
    }

    private fun HookValue.seedSaveType(): String = when (this) {
        is StringHookValue -> "string"
        is IntHookValue -> "int"
        is FloatHookValue -> "float"
        is BooleanHookValue -> "bool"
        else -> error("Seed save files only support scalar values.")
    }

    private fun HookValue.seedSaveValue(): String = when (this) {
        is StringHookValue -> value
        is IntHookValue -> value.toString()
        is FloatHookValue -> value.toString()
        is BooleanHookValue -> value.toString()
        else -> error("Seed save files only support scalar values.")
    }
}
