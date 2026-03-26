package com.hackwars.rewrite.client

import com.hackwars.rewrite.protocol.FrameCodec
import com.hackwars.rewrite.protocol.MalformedFrameException
import com.hackwars.rewrite.protocol.ProtocolTimeoutPolicy
import com.hackwars.rewrite.protocol.RewriteFrames
import com.hackwars.rewrite.protocol.RewriteService
import hackwars.rewrite.v1.FrameEnvelope
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class RewriteGameConnectionConfig(
    val host: String = System.getProperty("hackwars.rewrite.game.host", "127.0.0.1"),
    val port: Int = System.getProperty("hackwars.rewrite.game.port", "15020").toInt(),
    val clientBuild: String = System.getProperty("hackwars.rewrite.client.build", "rewrite-client-dev"),
    val bootstrapTimeout: Duration = 8.seconds,
)

class RewriteTcpServiceSessionGateway(
    private val gameConnectionConfig: RewriteGameConnectionConfig = RewriteGameConnectionConfig(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val socketFactory: (RewriteService) -> Socket = { service ->
        when (service) {
            RewriteService.GAME -> Socket().apply {
                tcpNoDelay = true
                connect(InetSocketAddress(gameConnectionConfig.host, gameConnectionConfig.port))
            }

            RewriteService.CHAT -> throw UnsupportedOperationException(
                "CHAT bootstrap is deferred in RW-CLIENT-002.",
            )
        }
    },
) : RewriteServiceSessionGateway {
    override fun open(
        service: RewriteService,
        onInboundFrame: (FrameEnvelope) -> Unit,
    ): RewriteServiceSession {
        return RewriteTcpServiceSession(
            service = service,
            socket = socketFactory(service),
            ioDispatcher = ioDispatcher,
            onInboundFrame = onInboundFrame,
        )
    }
}

private class RewriteTcpServiceSession(
    override val service: RewriteService,
    private val socket: Socket,
    private val ioDispatcher: CoroutineDispatcher,
    private val onInboundFrame: (FrameEnvelope) -> Unit,
) : RewriteServiceSession {
    private val output = BufferedOutputStream(socket.getOutputStream())
    private val input = BufferedInputStream(socket.getInputStream())
    private val closed = AtomicBoolean(false)
    private val readerScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val readerJob: Job = readerScope.launch {
        readLoop()
    }

    override suspend fun send(frame: FrameEnvelope) {
        withContext(ioDispatcher) {
            synchronized(output) {
                output.write(FrameCodec.encode(frame))
                output.flush()
            }
        }
    }

    override fun receive(frame: FrameEnvelope) {
        onInboundFrame(frame)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        runCatching { socket.close() }
        readerScope.cancel()
    }

    private suspend fun readLoop() {
        try {
            while (readerScope.isActive && !closed.get()) {
                val frame = readFrame(input)
                receive(frame)
            }
        } catch (exception: EOFException) {
            if (!closed.get()) {
                reportProblem("SERVER_DISCONNECTED", "The rewrite game server closed the connection.")
            }
        } catch (exception: MalformedFrameException) {
            if (!closed.get()) {
                reportProblem("MALFORMED_FRAME", "The rewrite game server sent a malformed frame.")
            }
        } catch (exception: Throwable) {
            if (!closed.get()) {
                reportProblem("CLIENT_IO_ERROR", "The rewrite game connection failed.")
            }
        } finally {
            close()
        }
    }

    private fun readFrame(input: InputStream): FrameEnvelope {
        val header = input.readExact(4)
        val declaredLength = ByteBuffer.wrap(header).int
        if (declaredLength < 0 || declaredLength > ProtocolTimeoutPolicy().maxFrameBytes) {
            throw MalformedFrameException("Declared frame length was invalid: $declaredLength")
        }
        val payload = input.readExact(declaredLength)
        return FrameCodec.decode(header + payload)
    }

    private fun InputStream.readExact(length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = read(buffer, offset, length - offset)
            if (read < 0) {
                throw EOFException("Unexpected end of stream.")
            }
            offset += read
        }
        return buffer
    }

    private fun reportProblem(code: String, message: String) {
        onInboundFrame(
            RewriteFrames.error(
                code = code,
                message = message,
                retryable = true,
            ),
        )
    }
}
