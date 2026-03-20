package com.hackwars.net

import assignments.MessageInPacket
import assignments.PingAssignment
import assignments.RemoteFunctionCall
import chat.client.ChatController
import com.hackwars.assignments.AssignmentEventDispatcher
import com.hackwars.assignments.IAssignmentEventDispatcher
import com.plink.dolphinnet.Assignment
import com.plink.dolphinnet.MessageClient
import com.plink.dolphinnet.assignments.ZippedAssignment
import org.slf4j.LoggerFactory
import java.util.*

class ConnectionManager : IAssignmentEventDispatcher by AssignmentEventDispatcher() {
    companion object {
        const val PING_TIMEOUT = 40000
        private val Logger = LoggerFactory.getLogger(ConnectionManager::class.java)
    }

    private val userId: String? = null
    private val username: String? = null
    var allowRun = false

    private var gameServerMessageClient: MessageClient? = null
    private var lastGameServerPing = 0L
    private var _isGameServerConnected = false
    private val isGameServerConnected: Boolean
        get() {
            return _isGameServerConnected && System.nanoTime() - lastGameServerPing > PING_TIMEOUT
        }

    private var chatServerMessageClient: MessageClient? = null
    private var lastChatServerPing = 0L
    private var _isChatServerConnected = false
    private val isChatServerConnected: Boolean
        get() {
            return _isChatServerConnected && System.nanoTime() - lastChatServerPing > PING_TIMEOUT
        }

    private val chatControllerLock = Any()
    var chatController: ChatController? = null
        set(value) {
            synchronized(chatControllerLock) {
                field = value
            }
        }

    private val tasks: ArrayList<Any?> = ArrayList<Any?>()
    fun addTask(task: Any?) {
        synchronized(tasks) {
            tasks.add(task)
        }
    }

    private val packets = ArrayList<Any?>()
    fun addPacket(packet: Any?) {
        synchronized(packets) {
            packets.add(packet)
        }
    }
    fun clearPackets() {
       synchronized(packets) {
           packets.clear()
       }
    }

    fun connectToGameServer() {
        Logger.debug("Initializing connection to game server")
        gameServerMessageClient = MessageClient(
            System.getProperty("hackwars.gameServer.address", "127.0.0.1"),
            200000,
            System.getProperty("hackwars.gameServer.inPort", "10021").toInt(),
            System.getProperty("hackwars.gameServer.outPort", "10020").toInt(),
        )
    }

    fun connectToChatServer() {
        Logger.debug("Initializing connection to chat server")
        chatServerMessageClient = MessageClient(
            System.getProperty("hackwars.chatServer.address", "127.0.0.1"),
            200000,
            System.getProperty("hackwars.chatServer.inPort", "10026").toInt(),
            System.getProperty("hackwars.chatServer.outPort", "10025").toInt(),
        )
    }

    fun startListening() {
        while (allowRun) {
            runCatching {
                tasks.iterator().let { taskIterator ->
                    while (taskIterator.hasNext()) {
                        when(val assignment = taskIterator.next()) {
                            is RemoteFunctionCall -> gameServerMessageClient?.addFinishedAssignment(ZippedAssignment(0, assignment))
                        }
                        taskIterator.remove()
                    }
                }
            }.onFailure {
                Logger.error("Error while running tasks", it)
            }

            for (j in packets.indices) {
                (packets[j] as? Assignment)?.let {
                    packets[j] = null
                    handleAssignmentPacket(it)
                }
            }

            //Remove processed packets.
            packets.iterator().run { while (hasNext()) next() ?: remove() }

            synchronized(chatControllerLock) {
                runCatching {
                    chatController?.popMessages()?.let {
                        if (isChatServerConnected)
                            chatServerMessageClient?.addFinishedAssignment(MessageInPacket(it))
                    }
                }.onFailure {
                    Logger.error("Unable to send chat message", it)
                }
            }

            if (System.nanoTime() - lastGameServerPing > PING_TIMEOUT) {
                lastGameServerPing = System.nanoTime()
                userId?.let {
                    Logger.debug("Been too long since last ping to game server, pinging to keep connection active")
                    gameServerMessageClient?.addFinishedAssignment(PingAssignment(0, it))
                }
            }
            if (System.nanoTime() - lastChatServerPing > PING_TIMEOUT) {
                lastChatServerPing = System.nanoTime()
                username?.let {
                    Logger.debug("Been too long since last ping to chat server, pinging to keep connection active")
                    chatServerMessageClient?.addFinishedAssignment(PingAssignment(0, it.lowercase(Locale.getDefault())))
                }
            }

            if (!isGameServerConnected) {
                // We made the assumption that the game server is no longer connected, so clean it up before we create a new one
                gameServerMessageClient
                    ?.runCatching { ::clean }
                    ?.onFailure {
                        Logger.error("Error while cleaning game server reporter", it)
                    }
                connectToGameServer()
            }

            if (!isChatServerConnected) {
                // We made the assumption that the chat server is no longer connected, so clean it up before we create a new one
                chatServerMessageClient
                    ?.runCatching { ::clean }
                    ?.onFailure {
                        Logger.error("Error while cleaning game server reporter", it)
                    }
                connectToChatServer()
            }
        }
    }
}