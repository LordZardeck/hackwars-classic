package com.hackwars.rewrite.persistence

import com.hackwars.rewrite.gamecore.ComputerEvent
import com.hackwars.rewrite.gamecore.ComputerState
import com.hackwars.rewrite.gamecore.RewriteGameJson

class ComputerStateSerializer {
    fun encodeState(state: ComputerState): ByteArray {
        return RewriteGameJson.encode(ComputerState.serializer(), state)
    }

    fun decodeState(payload: ByteArray): ComputerState {
        return RewriteGameJson.decode(ComputerState.serializer(), payload)
    }

    fun encodeEvent(event: ComputerEvent): ByteArray {
        return RewriteGameJson.encode(ComputerEvent.serializer(), event)
    }

    fun decodeEvent(payload: ByteArray): ComputerEvent {
        return RewriteGameJson.decode(ComputerEvent.serializer(), payload)
    }

    fun encodeStateJson(state: ComputerState): String {
        return RewriteGameJson.codec.encodeToString(ComputerState.serializer(), state)
    }

    fun decodeStateJson(payload: String): ComputerState {
        return RewriteGameJson.codec.decodeFromString(ComputerState.serializer(), payload)
    }
}
