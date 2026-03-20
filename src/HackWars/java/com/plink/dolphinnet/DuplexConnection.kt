package com.plink.dolphinnet

import com.plink.dolphinnet.assignments.ZippedAssignment
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.*
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException

/**
 * Represents an abstract duplex connection handling bi-directional communication over a socket.
 * This class is responsible for managing input/output streams, launching reader and writer jobs,
 * and ensuring graceful connection closure.
 *
 * Data being sent over the wire is serialized using Java's ObjectOutputStream and ObjectInputStream,
 * simplifying the read/write process and removing the need for a custom protocol. However, it's
 * terribly inefficient and should be replaced with a more efficient binary transport protocol such
 * as Protobuf
 *
 * @constructor
 * @param socket The socket instance associated with this connection.
 */
internal abstract class DuplexConnection(private var socket: Socket) {
    companion object {
        private val Logger: Logger = LoggerFactory.getLogger(DuplexConnection::class.java)
    }

    /**
     * A `SupervisorJob` instance used to manage the lifecycle of child coroutines within the `DuplexConnection` class.
     * Acts as a parent job that ensures structured concurrency by supervising its children,
     * allowing them to operate independently of failures in sibling coroutines.
     */
    private val job = SupervisorJob()
    /**
     * A private coroutine scope that manages the lifecycle of coroutines for the enclosing `DuplexConnection` class.
     * The scope is tied to a custom `job` to allow structured concurrency and uses the `IO` dispatcher
     * to offload blocking IO operations to a shared pool of threads.
     *
     * The combination of `job` and `Dispatchers.IO` ensures that tasks within this scope are properly
     * managed in terms of cancellation and optimized for IO-intensive operations.
     */
    private val scope = CoroutineScope(job + Dispatchers.IO)

    /**
     * Represents the background job responsible for reading data from the underlying communication
     * socket in the duplex connection. This job runs as a coroutine and facilitates
     * receiving and processing inbound data.
     *
     * It is initialized and managed internally by the [DuplexConnection] class. The lifecycle of
     * this job is tied to the duplex connection, and it is canceled when the connection is closed.
     *
     * A `null` value indicates that the reader job is not currently active.
     */
    private var readerJob: Job? = null

    /**
     * Represents an input stream used within the `DuplexConnection` class.
     * This variable holds a nullable `ObjectInputStream` instance that allows reading
     * serialized objects from an input source.
     *
     * The `inputStream` is initialized as `null` and is expected to be configured
     * when the connection is established. It is used to deserialize incoming
     * data as part of the read operation in the class.
     */
    private var inputStream: ObjectInputStream? = null

    /**
     * Represents the job responsible for managing the writer thread or coroutine in duplex communication.
     * This job is responsible for writing data to the output stream or outbound channel.
     * It is nullable to accommodate scenarios where the writer job might not be active or initialized.
     *
     * In the context of the DuplexConnection class, this job operates as part of the asynchronous
     * handling of outbound data and supports the lifecycle management of the writer process.
     */
    private var writerJob: Job? = null

    /**
     * Represents a high-capacity, unbounded channel used for sending outbound data in a
     * `DuplexConnection`. This channel facilitates asynchronous data flow, allowing multiple
     * coroutines to safely send objects without blocking. The objects sent via this channel
     * are expected to be further processed or transmitted by the writer job of the
     * `DuplexConnection`.
     *
     * The buffer capacity of this channel is set to unlimited, which means it can hold an
     * arbitrary number of objects until they are consumed. This design ensures that no backpressure
     * is applied to the sender coroutines, but care must be taken to avoid memory issues arising
     * from excessive or mismanaged usage.
     */
    private val outboundDataChannel = Channel<Any>(Channel.UNLIMITED)

    /**
     * The output stream used for writing serialized objects to the connected socket.
     *
     * This variable represents an optional instance of `ObjectOutputStream`, which is used
     * for sending data over the network connection. It is initialized when a connection is established
     * and can be null if the connection is not active or has been closed.
     *
     * The writer job primarily uses the output stream to transmit serialized objects
     * to the remote endpoint. Proper initialization and cleanup of this stream are crucial for
     * maintaining the integrity of the connection.
     *
     * Note: Access to this variable should be synchronized or managed within a controlled scope
     * to prevent concurrency issues during write operations.
     */
    private var outputStream: ObjectOutputStream? = null

    /**
     * Indicates whether the connection has been closed.
     *
     * This variable is used to track the state of the connection in the `DuplexConnection` class.
     * It helps prevent redundant closure operations or interactions with a connection that is
     * already closed. The default value is `false`, signifying that the connection is initially open.
     */
    protected var connectionClosed: Boolean = false
        private set

    /**
     * Lock object used to synchronize access to resources or operations that need to be thread-safe
     * within the context of the DuplexConnection class.
     * Acts as a monitor to ensure that only one thread can execute critical sections of code
     * that rely on this lock at a time.
     */
    private val closeLock = Any()

    /**
     * Launches a coroutine responsible for reading and processing incoming data from a socket connection.
     * The method cancels any previously active reading jobs before starting a new one.
     *
     * The coroutine continuously reads objects from the associated `inputStream`. If the object is an
     * instance of `ZippedAssignment`, it processes the enclosed assignment. For any other type of
     * object, it delegates processing to the `onReceiveObject` method.
     *
     * Exceptions occurring during data reading are handled as follows:
     * - `SocketTimeoutException`: Logged as a warning.
     * - `EOFException`, `SocketException`, or other generic exceptions: Logged as errors, and the reading
     *   job is canceled.
     *
     * The loop terminates and the connection is closed when either the connection is marked as closed
     * or a fatal error occurs.
     */
    private fun launchReaderJob() {
        // Close out any previous job that may have existed
        readerJob?.cancel()
        // Launch a new coroutine to read incoming data
        readerJob = scope.launch {
            var canceled = false

            while (true) {
                if (synchronized(closeLock) { connectionClosed || canceled }) break

                runCatching {
                    inputStream?.readObject()?.let { data ->
                        when (data) {
                            is ZippedAssignment -> onReceiveObject(data.getAssignment())
                            is Any -> onReceiveObject(data)
                        }
                    }
                }
                    .onFailure { exception ->
                        when (exception) {
                            is SocketTimeoutException -> Logger.warn("Socket timed out, unable to read data from socket")
                            is EOFException, is SocketException, is Exception -> {
                                Logger.error("Unexpected exception while reading data from socket", exception)
                                canceled = true
                            }
                        }
                    }
            }

            close()
        }
    }

    /**
     * Launches a coroutine responsible for writing outgoing data to a socket connection.
     *
     * This method ensures that any previously active writing job is canceled before starting a new one.
     * The coroutine continuously retrieves outgoing messages from the `outboundDataChannel` and writes
     * them to the associated `outputStream`. It handles interruptions and errors during this process:
     *
     * - If an error occurs while receiving data from `outboundDataChannel`, the loop is terminated after
     *   logging the error.
     * - If an error occurs while writing to the `outputStream`, the loop is also terminated after logging
     *   the error.
     *
     * The coroutine terminates and performs cleanup by invoking `close()` if the connection is marked
     * as closed or a fatal cancellation condition occurs.
     *
     * Thread-safety is maintained by synchronizing access to the shared `closeLock` resource.
     */
    private fun launchWriterJob() {
        writerJob?.cancel()
        writerJob = scope.launch {
            var canceled = false
            while (true) {
                if (synchronized(closeLock) { connectionClosed || canceled }) break

                val outgoing =
                    runCatching { outboundDataChannel.receive() }
                        .onFailure { exception ->
                            canceled = true
                            Logger.error("Unable to receive any more messages to send, closing connection", exception)
                        }
                        .getOrNull() ?: continue

                runCatching {
                    outputStream?.writeObject(outgoing)
                    outputStream?.flush()
                    outputStream?.reset()
                }.onFailure { exception ->
                    canceled = true
                    Logger.error("Unable to write to socket", exception)
                }
            }
            close()
        }
    }

    /**
     * Establishes a connection to the socket with the specified timeout settings and initiates
     * the reader and writer jobs for the data exchange over the connection.
     *
     * This method configures the socket to use the given timeout, enables TCP options for improved
     * data transfer efficiency, and initializes input and output streams for communication.
     * If the connection setup fails, the method logs the error and closes the connection.
     *
     * @param socketTimeOut The timeout value in milliseconds to set for the socket's read operations.
     */
    open fun connect(socketTimeOut: Int) {
        runCatching {
            socket.setSoTimeout(socketTimeOut)
            socket.setTcpNoDelay(true)
            socket.setKeepAlive(true)

            outputStream = ObjectOutputStream(BufferedOutputStream(socket.getOutputStream(), 20000))
            outputStream!!.flush()

            inputStream = ObjectInputStream(BufferedInputStream(socket.getInputStream(), 20000))
        }.onFailure { exception ->
            Logger.error("Unable to connect to socket", exception)
            close()
            return
        }

        this.launchReaderJob()
        this.launchWriterJob()
    }

    /**
     * Closes the duplex connection and releases all associated resources.
     *
     * This method ensures thread-safe cleanup of the connection's internal state by synchronizing
     * access to the `closeLock`. It performs the following actions:
     *
     * - Marks the connection as closed if it is not already closed.
     * - Cancels the `readerJob` and `writerJob` coroutines, which handle incoming and outgoing
     *   data processing, respectively.
     * - Attempts to close the `inputStream` and `outputStream`, associated with the underlying
     *   socket connection.
     * - Closes the connected `socket`, ensuring no further communication can occur.
     *
     * Any exceptions encountered during these operations are caught and ignored to guarantee
     * that all resources are released without interruption.
     *
     * Thread-safety is ensured by synchronizing access to the shared `closeLock` resource.
     */
    open fun close() {
        synchronized(closeLock) {
            connectionClosed = if (!connectionClosed) true else return

            runCatching { readerJob?.cancel() }
            runCatching { writerJob?.cancel() }
            runCatching { inputStream?.close() }
            runCatching { outputStream?.close() }
            runCatching { socket.close() }
        }
    }

    /**
     * Sends data through the outbound data channel.
     *
     * @param data The object to be sent over the connection. It can be any type of data that is compatible with the channel.
     */
    fun sendData(data: Any) {
        outboundDataChannel.trySend(data)
    }

    /**
     * Handles the reception of an object received over the duplex connection.
     *
     * This method is invoked whenever data is received through the connection.
     * The received object is processed depending on its type or purpose,
     * and custom actions should be implemented by subclasses to handle the data appropriately.
     *
     * @param data The object received from the peer through the connection. It could be any type of data depending on the use case.
     */
    abstract fun onReceiveObject(data: Any)
}
