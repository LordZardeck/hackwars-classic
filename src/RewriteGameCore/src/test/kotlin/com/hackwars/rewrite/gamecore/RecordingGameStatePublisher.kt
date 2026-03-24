package com.hackwars.rewrite.gamecore

class RecordingGameStatePublisher : GameStatePublisher {
    val snapshots = mutableListOf<Pair<Set<String>, ComputerState>>()
    val deltas = mutableListOf<Pair<Set<String>, ComputerDelta>>()
    val programUpdates = mutableListOf<Pair<Set<String>, ProgramUpdate>>()

    override suspend fun publishSnapshot(connectionIds: Set<String>, snapshot: ComputerState) {
        snapshots += connectionIds.toSet() to snapshot
    }

    override suspend fun publishDelta(connectionIds: Set<String>, delta: ComputerDelta) {
        deltas += connectionIds.toSet() to delta
    }

    override suspend fun publishProgramUpdate(connectionIds: Set<String>, update: ProgramUpdate) {
        programUpdates += connectionIds.toSet() to update
    }
}
