package com.plink.dolphinnet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.net.Socket
import kotlin.concurrent.Volatile

/**
 * A client implementation for managing assignments and communicating with a server.
 * The `MessageClient` class manages a connection to a server and processes data
 * asynchronously using coroutines.
 *
 * @constructor Initializes a `MessageClient` with the provided server address, port,
 * and socket timeout.
 * @param address The server address to connect to.
 * @param port The port of the server.
 * @param socketTimeOut The socket timeout, in milliseconds.
 */
class MessageClient(address: String?, port: Int, socketTimeOut: Int) {
    companion object {
        private val Logger = LoggerFactory.getLogger(MessageClient::class.java)
    }

    /**
     * Represents the unique identifier of a client in the system. This value is initialized
     * to -1, indicating that it has not yet been set. It plays a critical role in tracking
     * and identifying clients within the context of the `MessageClient` class operations.
     *
     * This property is read-only externally and can only be modified within the class.
     */
    var clientId: Int = -1
        private set
    /**
     * A variable that serves as a reference to a nullable implementation of the `DataHandler` interface.
     * The `DataHandler` interface is designed to distribute data-sets and manage assignment-related data operations
     * in a reporter-client system. This variable may be used to process and handle data for assignments
     * within the `MessageClient` class.
     *
     * This variable is expected to:
     * - Enable access to operations for adding, resetting, and retrieving data.
     * - Facilitate the handling of finished assignments by integrating with the appropriate implementation.
     *
     * The instance referenced by this variable can be set dynamically or left as `null`
     * depending on the operational requirements of the `MessageClient`.
     */
    var dataHandler: DataHandler? = null

    private val runAssignmentsJobScope = SupervisorJob()
    private val runAssignmentsScope = CoroutineScope(runAssignmentsJobScope + Dispatchers.IO)
    private val runAssignmentsChannel = Channel<Assignment>(Channel.UNLIMITED)
    /**
     * Represents a background job that continuously processes assignments received through the `runAssignmentsChannel`.
     * This coroutine is launched within the `runAssignmentsScope` and operates in a loop until it is explicitly canceled
     * or encounters a failure while receiving assignments.
     *
     * The job performs the following operations:
     * - Attempts to receive the next assignment from the `runAssignmentsChannel`.
     * - If an exception occurs while receiving, the job is marked as canceled, and an error is logged.
     * - For each successfully received assignment:
     *   - Calls the `execute` method of the assignment, passing in the `dataHandler`, to perform the task associated with the assignment.
     *   - Adds the resulting data from the execution to the `dataHandler` using its `addData` method.
     *   - Updates the `reporterID` of the assignment to match the unique client ID of the current `MessageClient`.
     *
     * The job is an integral part of the `MessageClient`'s operations, facilitating the continuous processing of tasks
     * assigned to it through the channel.
     */
    private val runAssignmentsJob = runAssignmentsScope.launch {
        var canceled = false

        while (!canceled) {
            val nextAssignment =
                runCatching { runAssignmentsChannel.receive() }
                    .onFailure { exception ->
                        canceled = true
                        Logger.error("Unable to receive any more assignments to process", exception)
                    }
                    .getOrNull() ?: continue

            dataHandler?.addData(nextAssignment.execute(dataHandler))
            nextAssignment.reporterID = this@MessageClient.clientId
        }
    }
    private var connection = ClientConnection(Socket(address, port))

    private inner class ClientConnection(socket: Socket) : DuplexConnection(socket) {
        /**
         * Handles the reception of an object and processes it based on its type.
         *
         * For objects of type `Assignment`, the method invokes the `runAssignment` method
         * in the associated `MessageClient` instance to process the assignment.
         *
         * For objects of type `Int`, the method updates the `clientId` of the `MessageClient`
         * instance if the integer value is non-negative. Otherwise, it terminates
         * all ongoing assignments by invoking the `killAllAssignments` method.
         *
         * Logs an error if any exception occurs during the execution.
         *
         * @param data The object received for processing. It is expected to be either of type `Assignment`
         *             for task-related operations or of type `Int` for updating the client ID
         *             or signaling assignment termination.
         */
        override fun onReceiveObject(data: Any) {
            runCatching {
                when (data) {
                    is Assignment -> this@MessageClient.runAssignment(data)
                    is Int -> {
                        if (data > -1) {
                            this@MessageClient.clientId = data
                            return
                        }

                        this@MessageClient.killAllAssignments()
                    }
                }
            }.onFailure { Logger.error("Error receiving data", it) }
        }
    }

    fun clean() {
        connection.close()
        dataHandler = null
        killAllAssignments()
        runAssignmentsJob.cancel()
    }

    /**
     * Kill all the assignments that are currently running.
     */
    @Synchronized
    fun killAllAssignments() {
        while (runAssignmentsChannel.tryReceive().isSuccess) {
            // Discard the received element
        }
    }

    init {
        Logger.info("Connecting to server $address:$port...")
        runCatching { connection.connect(socketTimeOut) }
            .onFailure { Logger.error("Unable to connect to server $address:$port", it) }
    }

    /**
     * Assigns the given assignment to the current client and sends it through the run assignments channel.
     *
     * This method is synchronized to ensure thread safety during the assignment process.
     *
     * @param assignment The assignment to be processed, where the reporterID is set to the ID of the current client
     *                   before being sent through the runAssignmentsChannel.
     */
    @Synchronized
    fun runAssignment(assignment: Assignment) {
        assignment.reporterID = this.clientId
        runAssignmentsChannel.trySend(assignment)
    }

    /**
     * Marks the given assignment as finished by setting its `reporterID` to the current client's ID
     * and sends the assignment through the established connection.
     *
     * This method is synchronized to ensure thread safety when handling the assignment data.
     *
     * @param assignment The assignment to be marked as finished and sent. The reporter ID is updated
     *                   to match the ID of the current client before transmission.
     */
    @Synchronized
    fun addFinishedAssignment(assignment: Assignment) {
        assignment.reporterID = this.clientId
        connection.sendData(assignment)
    }
}
