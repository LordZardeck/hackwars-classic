package com.hackwars.networking

import com.google.protobuf.Any
import com.google.protobuf.ByteString
import com.google.protobuf.Message
import com.hackwars.networking.auth.v1.AuthRequest
import com.hackwars.networking.auth.v1.AuthResponse
import com.hackwars.networking.v1.Request
import com.hackwars.networking.v1.Response
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.reflect.KClass

private const val SOCKET_TIMEOUT: Int = 5000

/**
 * Represents a connection to a remote service endpoint and facilitates sending messages over the connection.
 *
 * @constructor Initializes a `ServiceConnection` instance with the specified endpoint and port.
 * @param endpoint The hostname or IP address of the remote service.
 * @param port The port number of the remote service.
 */
class ServiceConnection(private val endpoint: String, private val port: Int) {
    /**
     * Guards request lifecycle state shared across request, listener, and close paths.
     */
    private val requestStateLock = Any()
    /**
     * Serializes reads/writes of the nullable socket reference so callers cannot
     * observe it while authenticate is setting it up.
     */
    private val socketLock = Any()

    /**
     * A private coroutine scope used internally by the `ServiceConnection` class to manage
     * asynchronous tasks.
     *
     * This scope is backed by a `SupervisorJob` and runs on the IO dispatcher, allowing
     * independent coroutines to be launched for non-blocking IO operations without affecting
     * one another in case of a failure in any individual task.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Tracks the identifiers of currently active requests sent over the service connection.
     *
     * This set is used to ensure that each outgoing request has a unique identifier
     * that does not collide with other active requests
     */
    private val activeRequests: HashSet<Short> = HashSet()

    /**
     * Establishes a TCP connection to the remote service using the specified endpoint and port.
     *
     * This socket is used internally by the `ServiceConnection` class to send and receive
     * messages to and from the remote service. The connection is initiated when the socket
     * is created and remains active for the lifetime of the `ServiceConnection` instance,
     * unless explicitly closed or interrupted.
     *
     * Thread-safety: Concurrent access to this socket is managed internally by the class
     * to support synchronized message sending.
     */
    private var socket: Socket? = null

    /**
     * Tracks the next available unique identifier for requests sent over the service connection.
     *
     * This variable ensures that every message sent to the remote service has a unique identifier.
     * The identifier is incremented sequentially, starting from the minimum value of the `Short` type.
     * If the value overflows, it wraps around to the minimum value of `Short`. The sequential strategy
     * helps to avoid collisions with identifiers in `activeRequests`.
     *
     * Thread-Safety: Access to this variable is synchronized to prevent race conditions during
     * concurrent request sending.
     */
    private var nextRequestId: Short = Short.MIN_VALUE

    /**
     * Represents the coroutine job responsible for listening to incoming messages
     * on the service connection's socket.
     *
     * This field is used to manage the lifecycle of the listening coroutine. If active,
     * the `listenJob` facilitates receiving and processing incoming messages. It may be
     * canceled to stop the listening process, typically during cleanup or when the
     * connection is closed.
     *
     * A null `listenJob` indicates that no active listening process exists.
     */
    private var listenJob: Job? = null

    /**
     * Maps request identifiers to their corresponding callback functions.
     *
     * When a response is received for a particular request ID, the associated callback
     * is invoked with the response data. Callbacks are removed from the map once invoked.
     *
     * Thread-safety: Access to this map is synchronized to prevent race conditions during
     * concurrent request/response handling.
     */
    private val requestCallbacks: HashMap<Short, (Any) -> Unit> = HashMap()

    /**
     * Reads and processes an incoming message from the associated socket. Decodes the received
     * frame, parses the payload into a `Response` object, and attempts to match the response
     * to a pending request based on the request ID. If a matching request is found, its
     * corresponding callback is invoked with the payload. If no matching request is found,
     * logs a message indicating the request is unknown.
     */
    private fun readIncomingMessage() {
        val frame = decodeFrame(checkNotNull(socket) { "Socket is not initialized" }.getInputStream())
        val message = Response.parseFrom(frame.payload)
        val requestId = message.requestId.toShort()

        if (frame.messageType != FrameMessageType.SERVICE) {
            // TODO: Replace with proper logging system
            println("Received non-service frame, discarded")
            return
        }

        val callback = synchronized(requestStateLock) {
            requestCallbacks[requestId]
        }
        if (callback == null) {
            // TODO: Replace with proper logging system
            println("Received response for unknown request: ${requestId}")
            return
        }
        callback.invoke(message.payload)
    }

    /**
     * Retrieves the next available request ID, ensuring that it is unique,
     * adds it to the set of active requests, and then returns it.
     *
     * @return The next available unique request ID as a `Short` value.
     */
    private fun getNextRequest(): Short {
        return synchronized(requestStateLock) {
            while (activeRequests.contains(nextRequestId)) nextRequestId++
            activeRequests.add(nextRequestId)
            nextRequestId
        }
    }

    /**
     * Awaits a response message corresponding to a given request ID and response type.
     * The method suspends the coroutine until a response is received or the coroutine is cancelled.
     *
     * @param requestId The unique identifier for the request whose response is awaited.
     * @param responseClass The class type of the expected response message.
     * @return The response message unpacked into the specified type of `ResponseMessage`.
     */
    private suspend fun <ResponseMessage : Message> waitForResponse(
        requestId: Short,
        responseClass: KClass<ResponseMessage>
    ): ResponseMessage =
        suspendCancellableCoroutine { continuation ->
            synchronized(requestStateLock) {
                requestCallbacks[requestId] = { response ->
                    synchronized(requestStateLock) {
                        requestCallbacks.remove(requestId)
                        activeRequests.remove(requestId)
                    }
                    // TODO: Handle failure in which the response was not the expected message type
                    val unpacked = runCatching { response.unpack(responseClass.java) }
                    continuation.resumeWith(unpacked)
                }
            }
            continuation.invokeOnCancellation {
                synchronized(requestStateLock) {
                    requestCallbacks.remove(requestId)
                    activeRequests.remove(requestId)
                }
            }
        }

    /**
     * Authenticates a connection using the provided authentication token.
     * The method establishes a socket connection to the defined endpoint and port,
     * initiates a listener coroutine for incoming messages, and sends an
     * authentication request using the provided token.
     *
     * @param authToken The authentication token as a byte array, used to verify the connection.
     * @return A boolean indicating whether the authentication was successful.
     */
    fun authenticate(authToken: ByteArray): Boolean {
        socket = synchronized(socketLock) {
            val newSocket = Socket()
            newSocket.keepAlive = true
            newSocket.connect(InetSocketAddress(endpoint, port), SOCKET_TIMEOUT)
            newSocket
        }

        listenJob = scope.launch {
            try {
                while (isActive) {
                    readIncomingMessage()
                }
            } catch (e: Exception) {
                // TODO: Replace with proper logging system
                println("Socket listener error: ${e.message}")
            } finally {
                close()
            }
        }

        return request(
            AuthRequest.newBuilder().setJwt(ByteString.copyFrom(authToken)).build(),
            AuthResponse::class
        ).hasAccepted()
    }

    /**
     * Sends a request message to a connected service and waits for a specific type of response.
     * This method encodes the provided message, sends it over the socket, and suspends the current coroutine
     * until a response of the expected type is received.
     *
     * @param ResponseMessage The expected type of the response message.
     * @param message The message to be sent in the request.
     * @param responseClass The `KClass` representing the type of the expected response message.
     * @return The response message unpacked into the specified type `ResponseMessage`.
     */
    fun <ResponseMessage : Message> request(message: Message, responseClass: KClass<ResponseMessage>): ResponseMessage {
        val requestId = getNextRequest()
        val requestMessage = Request.newBuilder().setRequestId(requestId.toInt()).setPayload(Any.pack(message)).build()
        val encodedFrame = encodeFrame(FrameMessageType.SERVICE, requestMessage)

        return runBlocking {
            val waitJob = async(start = CoroutineStart.UNDISPATCHED) {
                waitForResponse(requestId, responseClass)
            }

            try {
                synchronized(socketLock) {
                    val outputStream = checkNotNull(socket) { "Socket is not initialized" }.getOutputStream()
                    outputStream.write(encodedFrame)
                    outputStream.flush()
                }
            } catch (t: Throwable) {
                waitJob.cancel("Failed to write request frame.", t)
                throw t
            }

            waitJob.await()
        }
    }

    /**
     * Closes the active service connection by canceling any ongoing listening job,
     * releasing resources associated with the socket, and clearing all pending
     * active requests.
     *
     * This method ensures that the connection is properly shut down by performing
     * the following steps:
     * - Cancels the listening job, if one is active, to stop processing incoming messages.
     * - Closes the socket to terminate the underlying communication channel.
     * - Clears the set of active requests to release any held references and resources.
     */
    fun close() {
        listenJob?.cancel()
        synchronized(requestStateLock) {
            activeRequests.clear()
            requestCallbacks.clear()
        }
        synchronized(socketLock) {
            socket?.close()
            socket = null
        }
    }
}
