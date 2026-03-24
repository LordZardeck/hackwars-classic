package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.ApplicationKind
import com.hackwars.rewrite.gamecore.CompiledBinaryMetadata
import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.StoredFile
import com.hackwars.rewrite.gamecore.StoredFileKind
import com.hackwars.rewrite.gamecore.buildFilePath
import com.hackwars.rewrite.gamecore.ensureDirectory
import com.hackwars.rewrite.gamecore.saveFile
import java.sql.Connection
import java.sql.Timestamp

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
                    is SeedComputerState -> upsertComputerState(connection, payload)
                    is SeedInventorySnapshot -> applyInventorySnapshot(connection, payload)
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

    private fun upsertComputerState(
        connection: Connection,
        payload: SeedComputerState,
    ) {
        val stateId = GameStateId(payload.ipAddress)
        val state = ComputerState.empty(
            id = stateId,
            playerIp = payload.ipAddress,
            displayName = payload.playerId,
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
            statement.setString(1, payload.ipAddress)
            statement.setString(2, payload.playerId)
            statement.setString(3, payload.ipAddress)
            statement.setString(4, serializer.encodeStateJson(state))
            statement.executeUpdate()
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
        val updated = current.copy(
            filesystem = filesystem,
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
    }

    private fun seedPayloadJson(payload: SeedPayload): String {
        return when (payload) {
            is SeedPlayerAccount -> """{"type":"player","playerId":"${payload.playerId}","playFabId":"${payload.playFabId}","playerIp":"${payload.playerIp}"}"""
            is SeedComputerState -> """{"type":"computer","computerId":"${payload.computerId}","playerId":"${payload.playerId}","ipAddress":"${payload.ipAddress}"}"""
            is SeedInventorySnapshot -> {
                val notesJson = payload.notes.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                """{"type":"inventory","computerId":"${payload.computerId}","notes":$notesJson}"""
            }
        }
    }
}
