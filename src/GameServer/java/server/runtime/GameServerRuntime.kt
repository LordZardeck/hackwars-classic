package server.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext

class GameServerRuntime(
    parentContext: CoroutineContext = Dispatchers.Default
) : AutoCloseable {
    private val supervisor = SupervisorJob()
    private val services = CopyOnWriteArrayList<GameServerService>()

    val scope: CoroutineScope = CoroutineScope(parentContext + supervisor + CoroutineName("GameServerRuntime"))
    val serialDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    fun childScope(
        name: String,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ): CoroutineScope {
        return CoroutineScope(scope.coroutineContext + dispatcher + CoroutineName(name))
    }

    fun serialScope(name: String): CoroutineScope {
        return childScope(name, serialDispatcher)
    }

    fun <T : GameServerService> register(service: T): T {
        services += service
        return service
    }

    fun startRegisteredServices() {
        services.forEach(GameServerService::start)
    }

    fun shutdownRegisteredServices() {
        services.asReversed().forEach(GameServerService::shutdown)
    }

    suspend fun joinRegisteredServices() {
        services.asReversed().forEach { service ->
            service.join()
        }
    }

    suspend fun shutdownAndJoin() {
        shutdownRegisteredServices()
        joinRegisteredServices()
        supervisor.cancelAndJoin()
    }
    override fun close() {
        scope.cancel()
    }
}
