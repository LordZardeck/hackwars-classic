package com.hackwars.rewrite.client.login

object RewriteClientClock {
    @Volatile
    private var frozenTime: FrozenTime? = null

    fun nowMillis(): Long = frozenTime?.millis ?: System.currentTimeMillis()

    fun nowNanos(): Long = frozenTime?.nanos ?: System.nanoTime()

    @Synchronized
    fun freezeForTest(millis: Long, nanos: Long) {
        frozenTime = FrozenTime(millis = millis, nanos = nanos)
    }

    @Synchronized
    fun advanceForTest(millisDelta: Long, nanosDelta: Long) {
        val current = frozenTime ?: FrozenTime(
            millis = System.currentTimeMillis(),
            nanos = System.nanoTime(),
        )
        frozenTime = FrozenTime(
            millis = current.millis + millisDelta,
            nanos = current.nanos + nanosDelta,
        )
    }

    @Synchronized
    fun resetForTest() {
        frozenTime = null
    }

    private data class FrozenTime(
        val millis: Long,
        val nanos: Long,
    )
}
