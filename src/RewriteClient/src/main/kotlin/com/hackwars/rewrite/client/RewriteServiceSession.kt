package com.hackwars.rewrite.client

import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope

interface RewriteServiceSession : AutoCloseable {
    val service: RewriteService

    suspend fun send(frame: FrameEnvelope)

    fun receive(frame: FrameEnvelope)

    override fun close()
}

fun interface RewriteServiceSessionGateway {
    fun open(
        service: RewriteService,
        onInboundFrame: (FrameEnvelope) -> Unit,
    ): RewriteServiceSession
}

object NoOpRewriteServiceSessionGateway : RewriteServiceSessionGateway {
    override fun open(
        service: RewriteService,
        onInboundFrame: (FrameEnvelope) -> Unit,
    ): RewriteServiceSession {
        return NoOpRewriteServiceSession(
            service = service,
            onInboundFrame = onInboundFrame,
        )
    }
}

private class NoOpRewriteServiceSession(
    override val service: RewriteService,
    private val onInboundFrame: (FrameEnvelope) -> Unit,
) : RewriteServiceSession {
    override suspend fun send(frame: FrameEnvelope) {
        Unit
    }

    override fun receive(frame: FrameEnvelope) {
        onInboundFrame(frame)
    }

    override fun close() {
        Unit
    }
}
