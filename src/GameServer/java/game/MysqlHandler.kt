package game

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import server.runtime.GameServerRuntime
import server.runtime.GameServerService
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean

object MysqlHandler {
    private val defaultCoordinator = SaveCoordinator()

    @JvmField
    var SAVE_COUNTER = 0

    @JvmStatic
    fun getWork(): Array<Any?>? {
        return defaultCoordinator.peekWork()
    }

    @JvmStatic
    fun addWork(work1: Array<Any?>) {
        defaultCoordinator.enqueue(work1)
    }

    @JvmStatic
    fun start() {
        defaultCoordinator.start()
    }

    @JvmStatic
    fun shutdown() {
        defaultCoordinator.shutdown()
    }

    @JvmStatic
    suspend fun awaitIdle() {
        defaultCoordinator.awaitIdle()
    }

    @JvmStatic
    suspend fun shutdownAndJoin() {
        defaultCoordinator.shutdownAndJoin()
    }
}

internal class SaveCoordinator(
    private val runtime: GameServerRuntime = GameServerRuntime(),
    private val workerDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
    private val workerCount: Int = 10,
    private val workerFactory: () -> CheckOutHandler = { CheckOutHandler() }
) : GameServerService {
    private val lock = Any()
    private val pending = LinkedHashMap<String, Array<Any?>>()
    private val signals = Channel<String>(Channel.UNLIMITED)
    private val workers = ArrayList<Job>()
    private val started = AtomicBoolean(false)
    private val shuttingDown = AtomicBoolean(false)
    @Volatile
    private var activeWorkers = 0
    @Volatile
    private var lastSave = 0L

    override fun start() {
        if (shuttingDown.get()) {
            return
        }
        if (!started.compareAndSet(false, true)) {
            return
        }

        repeat(workerCount) { index ->
            workers += runtime.scope.launch(workerDispatcher + CoroutineName("MysqlHandler-$index")) {
                workerLoop(workerFactory())
            }
        }
    }

    override fun shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return
        }

        val outstandingIps = synchronized(lock) {
            pending.keys.toList()
        }

        outstandingIps.forEach {
            signals.trySend(it)
        }
        signals.close()
    }

    override suspend fun join() {
        workers.toList().joinAll()
        runtime.close()
    }

    suspend fun shutdownAndJoin() {
        shutdown()
        join()
    }

    fun enqueue(work: Array<Any?>) {
        val ip = work.firstOrNull() as? String ?: return

        if (shuttingDown.get()) {
            return
        }

        start()
        synchronized(lock) {
            val previous = pending.put(ip, work)
            if (previous == null) {
                MysqlHandler.SAVE_COUNTER++
            }
        }

        signals.trySend(ip)
    }

    fun peekWork(): Array<Any?>? {
        start()
        return synchronized(lock) { pending.values.firstOrNull() }
    }

    suspend fun awaitIdle() {
        while (true) {
            val idle = synchronized(lock) { pending.isEmpty() && activeWorkers == 0 }
            if (idle) {
                return
            }
            delay(10)
        }
    }

    internal fun pendingCount(): Int {
        return synchronized(lock) { pending.size }
    }

    private suspend fun workerLoop(processor: CheckOutHandler) {
        for (ip in signals) {
            val work = synchronized(lock) {
                val current = pending[ip] ?: return@synchronized null
                activeWorkers++
                current
            } ?: continue

            try {
                processor.processWork(work)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                synchronized(lock) {
                    if (pending[ip] === work) {
                        pending.remove(ip)
                        MysqlHandler.SAVE_COUNTER--
                        lastSave = ServerRuntimeState.now()
                    }
                    activeWorkers--
                }
            }
        }
    }
}
