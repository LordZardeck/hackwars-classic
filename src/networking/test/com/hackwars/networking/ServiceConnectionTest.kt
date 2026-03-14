package com.hackwars.networking

import com.google.protobuf.Any
import com.google.protobuf.Message
import com.hackwars.networking.auth.v1.AuthAccepted
import com.hackwars.networking.auth.v1.AuthRequest
import com.hackwars.networking.auth.v1.AuthResponse
import com.hackwars.networking.chat.v1.ChatPingRequest
import com.hackwars.networking.chat.v1.ChatPingResponse
import com.hackwars.networking.v1.Request
import com.hackwars.networking.v1.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.EOFException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ServiceConnectionTest {

    @Test
    fun `getNextRequest increments and tracks active requests`() {
        val connection = ServiceConnection(LOCALHOST, 65535)
        val getNextRequestMethod = ServiceConnection::class.java.getDeclaredMethod("getNextRequest")
        getNextRequestMethod.isAccessible = true

        val first = getNextRequestMethod.invoke(connection) as Short
        val second = getNextRequestMethod.invoke(connection) as Short

        assertEquals(Short.MIN_VALUE, first)
        assertEquals((Short.MIN_VALUE + 1).toShort(), second)
        assertEquals(2, activeRequestsSize(connection))
    }

    @Test
    fun `getNextRequest should generate unique ids under concurrency`() {
        val connection = ServiceConnection(LOCALHOST, 65535)
        val threads = 12
        val idsPerThread = 500
        val startBarrier = CyclicBarrier(threads)

        val getNextRequestMethod = ServiceConnection::class.java.getDeclaredMethod("getNextRequest")
        getNextRequestMethod.isAccessible = true

        val executor = Executors.newFixedThreadPool(threads)
        try {
            val futures = (0 until threads).map {
                executor.submit(Callable {
                    startBarrier.await(2, TimeUnit.SECONDS)
                    List(idsPerThread) { getNextRequestMethod.invoke(connection) as Short }
                })
            }
            val ids = futures.flatMap { it.get(5, TimeUnit.SECONDS) }

            assertEquals(threads * idsPerThread, ids.size)
            assertEquals(ids.size, ids.toSet().size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `close should not race with in-flight getNextRequest`() {
        val connection = ServiceConnection(LOCALHOST, 65535)
        val guardedSet = ConcurrencyDetectingHashSet()
        setPrivateField(connection, "activeRequests", guardedSet)
        setSocket(connection, Socket()) // request will eventually fail on write, but after getNextRequest path

        val requestExecutor = Executors.newSingleThreadExecutor()
        try {
            val requestFuture = requestExecutor.submit(Callable {
                runCatching {
                    connection.request(ChatPingRequest.getDefaultInstance(), ChatPingResponse::class)
                }
            })

            guardedSet.awaitContainsEntered()
            // This should not throw for a thread-safe implementation.
            connection.close()
            guardedSet.releaseContains()
            requestFuture.get(2, TimeUnit.SECONDS)
        } finally {
            guardedSet.releaseContains()
            forceTerminate(connection)
            requestExecutor.shutdownNow()
        }
    }

    @Test
    fun `request sends envelope and unpacks response`() {
        val requestSeen = AtomicReference<Request>()

        ServerSocket(0).use { server ->
            val acceptExecutor = Executors.newSingleThreadExecutor()
            val readerExecutor = Executors.newSingleThreadExecutor()
            try {
                val serverFuture = acceptExecutor.submit(Callable {
                    server.accept().use { socket ->
                        val frame = readStrictFrame(socket)
                        assertEquals(FrameMessageType.SERVICE.wireValue.toInt(), frame.typeWire)

                        val request = Request.parseFrom(frame.payload)
                        requestSeen.set(request)
                        request.payload.unpack(ChatPingRequest::class.java)

                        val response =
                            ChatPingResponse.newBuilder()
                                .setServerId("chat-1")
                                .setServerUnixMs(1700000000000L)
                                .build()
                        writeServiceResponse(socket, request.requestId, response)
                    }
                })

                val clientSocket = Socket(LOCALHOST, server.localPort)
                val connection = ServiceConnection(LOCALHOST, server.localPort)
                setSocket(connection, clientSocket)

                val responseFuture = readerExecutor.submit(Callable {
                    connection.request(ChatPingRequest.getDefaultInstance(), ChatPingResponse::class)
                })

                waitUntil(1_000) { requestCallbacksSize(connection) == 1 }
                waitUntil(1_000) { requestSeen.get() != null }
                invokeReadIncomingMessage(connection)
                val response = responseFuture.get(3, TimeUnit.SECONDS)

                assertEquals("chat-1", response.serverId)
                assertEquals(1700000000000L, response.serverUnixMs)
                assertEquals(0, activeRequestsSize(connection))
                assertEquals(Short.MIN_VALUE.toInt(), requestSeen.get().requestId)

                serverFuture.get(3, TimeUnit.SECONDS)
                connection.close()
            } finally {
                readerExecutor.shutdownNow()
                acceptExecutor.shutdownNow()
            }
        }
    }

    @Test
    fun `request registers callback before attempting socket write`() {
        val connection = ServiceConnection(LOCALHOST, 65535)
        val requestExecutor = Executors.newSingleThreadExecutor()
        val socketLockField = ServiceConnection::class.java.getDeclaredField("socketLock")
        socketLockField.isAccessible = true
        val socketLock = socketLockField.get(connection)

        setSocket(connection, Socket()) // Intentionally unconnected; write path will fail after lock is released.

        try {
            val requestFuture = synchronized(socketLock) {
                val future = requestExecutor.submit(Callable<Throwable?> {
                    try {
                        connection.request(ChatPingRequest.getDefaultInstance(), ChatPingResponse::class)
                        null
                    } catch (t: Throwable) {
                        t
                    }
                })

                waitUntil(1_000) { requestCallbacksSize(connection) == 1 }
                assertEquals(1, activeRequestsSize(connection))
                future
            }

            val thrown = requestFuture.get(2, TimeUnit.SECONDS)
            assertNotNull(thrown)
            waitUntil(1_000) {
                requestCallbacksSize(connection) == 0 && activeRequestsSize(connection) == 0
            }
        } finally {
            forceTerminate(connection)
            requestExecutor.shutdownNow()
        }
    }

    @Test
    fun `authenticate should succeed when server responds with accepted`() {
        val jwt = byteArrayOf(1, 2, 3, 4)

        ServerSocket(0).use { server ->
            val serverExecutor = Executors.newSingleThreadExecutor()
            val connection = ServiceConnection(LOCALHOST, server.localPort)
            try {
                val serverFuture = serverExecutor.submit(Callable {
                    server.accept().use { socket ->
                        val requestFrame = readStrictFrame(socket)
                        val request = Request.parseFrom(requestFrame.payload)
                        request.payload.unpack(AuthRequest::class.java)

                        val accepted =
                            AuthResponse.newBuilder()
                                .setAccepted(AuthAccepted.newBuilder().setSuccess(true).build())
                                .build()
                        writeServiceResponse(socket, request.requestId, accepted)
                    }
                })

                val authed = callWithTimeout(2) {
                    connection.authenticate(jwt)
                }
                assertTrue(authed)
                serverFuture.get(2, TimeUnit.SECONDS)
            } finally {
                forceTerminate(connection)
                serverExecutor.shutdownNow()
            }
        }
    }

    @Test
    fun `request should not deadlock while listener waits on socket`() {
        ServerSocket(0).use { server ->
            val serverExecutor = Executors.newSingleThreadExecutor()
            val listenerExecutor = Executors.newSingleThreadExecutor()
            var connection: ServiceConnection? = null
            var clientSocket: Socket? = null
            try {
                val serverFuture = serverExecutor.submit(Callable {
                    server.accept().use { socket ->
                        val request = Request.parseFrom(readStrictFrame(socket).payload)
                        request.payload.unpack(ChatPingRequest::class.java)
                        val response =
                            ChatPingResponse.newBuilder()
                                .setServerId("chat-deadlock")
                                .setServerUnixMs(42L)
                                .build()
                        writeServiceResponse(socket, request.requestId, response)
                    }
                })

                connection = ServiceConnection(LOCALHOST, server.localPort)
                clientSocket = Socket(LOCALHOST, server.localPort)
                setSocket(connection, clientSocket)

                // This blocks on read while holding socketLock.
                val listenerFuture = listenerExecutor.submit(Callable {
                    invokeReadIncomingMessage(connection)
                })

                val response = callWithTimeout(2) {
                    connection.request(ChatPingRequest.getDefaultInstance(), ChatPingResponse::class)
                }

                assertEquals("chat-deadlock", response.serverId)
                serverFuture.get(2, TimeUnit.SECONDS)
                listenerFuture.get(2, TimeUnit.SECONDS)
                connection.close()
            } finally {
                runCatching { connection?.let { forceTerminate(it) } }
                runCatching { clientSocket?.close() }
                listenerExecutor.shutdownNow()
                serverExecutor.shutdownNow()
            }
        }
    }

    @Test
    fun `request throws when socket is not initialized`() {
        val connection = ServiceConnection(LOCALHOST, 65535)

        assertThrows(IllegalStateException::class.java) {
            connection.request(ChatPingRequest.getDefaultInstance(), ChatPingResponse::class)
        }
    }

    @Test
    fun `close is idempotent`() {
        val connection = ServiceConnection(LOCALHOST, 65535)
        connection.close()
        connection.close()
    }

    @Test
    fun `authenticate propagates connect failure`() {
        val unavailablePort = ServerSocket(0).use { it.localPort }
        val connection = ServiceConnection(LOCALHOST, unavailablePort)

        assertThrows(java.io.IOException::class.java) {
            connection.authenticate(byteArrayOf(1))
        }
    }

    private data class StrictFrame(val typeWire: Int, val payload: ByteArray)

    private fun readStrictFrame(socket: Socket): StrictFrame {
        val input = DataInputStream(socket.getInputStream())
        val typeWire = input.readUnsignedByte()
        val payloadLength = input.readInt()
        val payload = input.readNBytes(payloadLength)

        if (payload.size != payloadLength) {
            throw EOFException("Client frame ended before declared length: ${payload.size} != $payloadLength")
        }

        return StrictFrame(typeWire, payload)
    }

    private fun writeServiceResponse(socket: Socket, requestId: Int, message: Message) {
        val response = Response.newBuilder().setRequestId(requestId).setPayload(Any.pack(message)).build()
        val encodedFrame = encodeFrame(FrameMessageType.SERVICE, response)
        val output = socket.getOutputStream()
        output.write(encodedFrame)
        output.flush()
    }

    private fun setSocket(connection: ServiceConnection, socket: Socket) {
        val socketField = ServiceConnection::class.java.getDeclaredField("socket")
        socketField.isAccessible = true
        socketField.set(connection, socket)
    }

    private fun setPrivateField(connection: ServiceConnection, name: String, value: kotlin.Any) {
        val field = ServiceConnection::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(connection, value)
    }

    private fun forceTerminate(connection: ServiceConnection) {
        runCatching {
            val jobField = ServiceConnection::class.java.getDeclaredField("listenJob")
            jobField.isAccessible = true
            val job = jobField.get(connection) as? kotlinx.coroutines.Job
            job?.cancel()
        }
        runCatching {
            val socketField = ServiceConnection::class.java.getDeclaredField("socket")
            socketField.isAccessible = true
            val socket = socketField.get(connection) as? Socket
            socket?.close()
            socketField.set(connection, null)
        }
    }

    private fun invokeReadIncomingMessage(connection: ServiceConnection) {
        val method = ServiceConnection::class.java.getDeclaredMethod("readIncomingMessage")
        method.isAccessible = true
        method.invoke(connection)
    }

    @Suppress("UNCHECKED_CAST")
    private fun requestCallbacksSize(connection: ServiceConnection): Int {
        val field = ServiceConnection::class.java.getDeclaredField("requestCallbacks")
        field.isAccessible = true
        return (field.get(connection) as HashMap<Short, (Any) -> Unit>).size
    }

    @Suppress("UNCHECKED_CAST")
    private fun activeRequestsSize(connection: ServiceConnection): Int {
        val field = ServiceConnection::class.java.getDeclaredField("activeRequests")
        field.isAccessible = true
        return (field.get(connection) as HashSet<Short>).size
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.onSpinWait()
        }
        throw AssertionError("Condition was not met within ${timeoutMs}ms.")
    }

    private fun <T> callWithTimeout(seconds: Long, action: () -> T): T {
        val result = AtomicReference<T>()
        val error = AtomicReference<Throwable>()
        val done = CountDownLatch(1)

        val worker = Thread {
            try {
                result.set(action())
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                done.countDown()
            }
        }
        worker.isDaemon = true
        worker.start()

        if (!done.await(seconds, TimeUnit.SECONDS)) {
            throw TimeoutException("Action did not complete within ${seconds}s.")
        }

        error.get()?.let { throw it }
        return result.get()
    }

    companion object {
        private const val LOCALHOST = "127.0.0.1"
    }

    private class ConcurrencyDetectingHashSet : HashSet<Short>() {
        private val inUse = AtomicBoolean(false)
        private val containsEntered = CountDownLatch(1)
        private val releaseContains = CountDownLatch(1)

        fun awaitContainsEntered() {
            if (!containsEntered.await(2, TimeUnit.SECONDS)) {
                throw AssertionError("contains() was not reached in time.")
            }
        }

        fun releaseContains() {
            releaseContains.countDown()
        }

        override fun contains(element: Short): Boolean {
            enter()
            return try {
                containsEntered.countDown()
                releaseContains.await(2, TimeUnit.SECONDS)
                super.contains(element)
            } finally {
                exit()
            }
        }

        override fun add(element: Short): Boolean {
            enter()
            return try {
                super.add(element)
            } finally {
                exit()
            }
        }

        override fun remove(element: Short): Boolean {
            enter()
            return try {
                super.remove(element)
            } finally {
                exit()
            }
        }

        override fun clear() {
            enter()
            try {
                super.clear()
            } finally {
                exit()
            }
        }

        private fun enter() {
            if (!inUse.compareAndSet(false, true)) {
                throw IllegalStateException("activeRequests accessed concurrently without synchronization.")
            }
        }

        private fun exit() {
            inUse.set(false)
        }
    }
}
