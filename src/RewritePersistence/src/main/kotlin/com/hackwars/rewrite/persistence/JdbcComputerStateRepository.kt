package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.ComputerEvent
import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.ComputerStateRepository
import com.hackwars.rewrite.gamecore.GameStateId
import com.hackwars.rewrite.gamecore.SnapshotCoordinator
import com.hackwars.rewrite.gamecore.applyEvents
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class JdbcComputerStateRepository(
    private val connectionFactory: () -> Connection,
    private val serializer: ComputerStateSerializer = ComputerStateSerializer(),
    private val snapshotCoordinator: SnapshotCoordinator = SnapshotCoordinator(),
    private val clock: () -> Instant = { Instant.now() },
) : ComputerStateRepository {
    override suspend fun load(id: GameStateId): ComputerState? {
        return connectionFactory().use { connection ->
            load(connection, id, forUpdate = false)
        }
    }

    override suspend fun loadStates(ids: Collection<GameStateId>): Map<GameStateId, ComputerState> {
        if (ids.isEmpty()) {
            return emptyMap()
        }

        return connectionFactory().use { connection ->
            ids.distinct().mapNotNull { id ->
                load(connection, id, forUpdate = false)?.let { id to it }
            }.toMap()
        }
    }

    override suspend fun appendEvents(id: GameStateId, events: List<ComputerEvent>): ComputerState {
        if (events.isEmpty()) {
            return requireNotNull(load(id)) {
                "Cannot append zero events to missing state ${id.value}."
            }
        }

        return connectionFactory().use { connection ->
            connection.autoCommit = false
            try {
                val currentState = requireNotNull(load(connection, id, forUpdate = true)) {
                    "No rewrite_computer_state row exists for ${id.value}."
                }
                val now = clock()
                val updatedState = currentState.applyEvents(events)
                val startingSequence = currentState.version + 1

                events.forEachIndexed { index, event ->
                    persistEvent(
                        connection = connection,
                        streamId = id.value,
                        sequence = startingSequence + index,
                        payload = serializer.encodeEvent(event),
                        recordedAt = now,
                    )
                }
                maybeSnapshot(connection, id, updatedState, now)
                connection.commit()
                updatedState
            } catch (exception: Throwable) {
                connection.rollback()
                throw exception
            }
        }
    }

    private fun load(
        connection: Connection,
        id: GameStateId,
        forUpdate: Boolean,
    ): ComputerState? {
        val baseState = loadBaseState(connection, id, forUpdate) ?: return null
        val latestSnapshot = loadLatestSnapshot(connection, id)
        val seedState = latestSnapshot?.let { serializer.decodeState(it.payload) } ?: baseState
        val replayedEvents = loadEventsAfter(connection, id, latestSnapshot?.version ?: 0)
            .map { persistedEvent -> serializer.decodeEvent(persistedEvent.payload) }
        return seedState.applyEvents(replayedEvents)
    }

    private fun loadBaseState(
        connection: Connection,
        id: GameStateId,
        forUpdate: Boolean,
    ): ComputerState? {
        val lockClause = if (forUpdate) " for update" else ""
        connection.prepareStatement(
            """
            select state_payload::text
            from rewrite_computer_state
            where computer_id = ?
            $lockClause
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, id.value)
            statement.executeQuery().use { resultSet ->
                if (!resultSet.next()) {
                    return null
                }
                return serializer.decodeStateJson(resultSet.getString(1))
            }
        }
    }

    private fun loadLatestSnapshot(
        connection: Connection,
        id: GameStateId,
    ): PersistedSnapshot? {
        connection.prepareStatement(
            """
            select version, payload, recorded_at
            from rewrite_state_snapshot
            where stream_id = ?
            order by version desc
            limit 1
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, id.value)
            statement.executeQuery().use { resultSet ->
                if (!resultSet.next()) {
                    return null
                }
                return PersistedSnapshot(
                    streamId = id.value,
                    version = resultSet.getLong("version"),
                    payload = resultSet.getBytes("payload"),
                    recordedAt = resultSet.getTimestamp("recorded_at").toInstant(),
                )
            }
        }
    }

    private fun loadEventsAfter(
        connection: Connection,
        id: GameStateId,
        versionExclusive: Long,
    ): List<PersistedEvent> {
        connection.prepareStatement(
            """
            select sequence_number, payload, recorded_at
            from rewrite_state_event
            where stream_id = ?
              and sequence_number > ?
            order by sequence_number asc
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, id.value)
            statement.setLong(2, versionExclusive)
            statement.executeQuery().use { resultSet ->
                val events = mutableListOf<PersistedEvent>()
                while (resultSet.next()) {
                    events += PersistedEvent(
                        streamId = id.value,
                        sequence = resultSet.getLong("sequence_number"),
                        payload = resultSet.getBytes("payload"),
                        recordedAt = resultSet.getTimestamp("recorded_at").toInstant(),
                    )
                }
                return events
            }
        }
    }

    private fun persistEvent(
        connection: Connection,
        streamId: String,
        sequence: Long,
        payload: ByteArray,
        recordedAt: Instant,
    ) {
        connection.prepareStatement(
            """
            insert into rewrite_state_event(stream_id, sequence_number, payload, recorded_at)
            values (?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, streamId)
            statement.setLong(2, sequence)
            statement.setBytes(3, payload)
            statement.setTimestamp(4, Timestamp.from(recordedAt))
            statement.executeUpdate()
        }
    }

    private fun maybeSnapshot(
        connection: Connection,
        id: GameStateId,
        updatedState: ComputerState,
        now: Instant,
    ) {
        val latestSnapshot = loadLatestSnapshot(connection, id)
        val lastSnapshotVersion = latestSnapshot?.version ?: 0
        val eventsSinceLastSnapshot = (updatedState.version - lastSnapshotVersion).toInt()
        val elapsedSinceLastSnapshot = elapsedSinceLastSnapshot(connection, id, latestSnapshot, now)
        if (!snapshotCoordinator.shouldSnapshot(eventsSinceLastSnapshot, elapsedSinceLastSnapshot)) {
            return
        }

        connection.prepareStatement(
            """
            insert into rewrite_state_snapshot(stream_id, version, payload, recorded_at)
            values (?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, id.value)
            statement.setLong(2, updatedState.version)
            statement.setBytes(3, serializer.encodeState(updatedState))
            statement.setTimestamp(4, Timestamp.from(now))
            statement.executeUpdate()
        }
    }

    private fun elapsedSinceLastSnapshot(
        connection: Connection,
        id: GameStateId,
        latestSnapshot: PersistedSnapshot?,
        now: Instant,
    ): Duration {
        val referenceTime = latestSnapshot?.recordedAt ?: oldestUnsnapshottedEventTimestamp(
            connection = connection,
            id = id,
            versionExclusive = latestSnapshot?.version ?: 0,
        ) ?: now
        return (now.toEpochMilli() - referenceTime.toEpochMilli()).milliseconds
    }

    private fun oldestUnsnapshottedEventTimestamp(
        connection: Connection,
        id: GameStateId,
        versionExclusive: Long,
    ): Instant? {
        connection.prepareStatement(
            """
            select recorded_at
            from rewrite_state_event
            where stream_id = ?
              and sequence_number > ?
            order by sequence_number asc
            limit 1
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, id.value)
            statement.setLong(2, versionExclusive)
            statement.executeQuery().use { resultSet ->
                return if (resultSet.next()) {
                    resultSet.getTimestamp("recorded_at").toInstant()
                } else {
                    null
                }
            }
        }
    }
}
