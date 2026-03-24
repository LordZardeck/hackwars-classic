package game
import game.computer.runtime.SaveLogoutTickService
import game.payload.MessageTextPayload
import game.payload.WebPagePayload
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import server.runtime.GameServerRuntime
import server.runtime.GameServerService

/**
 * A central location for distributing messages to other computers.
 */
class ComputerHandler @JvmOverloads constructor(
    private val serverBridge: HackerServerBridge?,
    private var runtime: GameServerRuntime? = null
) : GameServerService {
    companion object {
        private val Logger = LoggerFactory.getLogger(ComputerHandler::class.java)
        private const val FAILED_REMOTE_LOAD_CACHE_MS = 5_000L
    }

    private val computerList = ComputerBinaryList()
    private val taskLock = Any()
    private val computerLock = Any()
    private val mailboxTasks = ArrayDeque<ApplicationDataTask>()
    private val failedRemoteLoads = HashMap<String, FailedRemoteLoad>()
    private var on = true
    private var playerCount = 0
    private var started = false
    private var active = false
    private var ownsRuntime = false
    private var serviceScope: CoroutineScope? = null
    private var processorJob: Job? = null
    private var mailboxSignal: Channel<Unit> = Channel(Channel.CONFLATED)

    private data class ApplicationDataTask(
        val applicationData: Any?,
        val ip: String?
    )

    private data class FailedRemoteLoad(
        val errorMessage: String,
        val expiresAtMillis: Long
    )

    @Synchronized
    fun start(runtime: GameServerRuntime? = null) {
        if (started) {
            return
        }

        val resolvedRuntime = runtime ?: this.runtime ?: GameServerRuntime().also {
            ownsRuntime = true
        }
        this.runtime = resolvedRuntime
        serviceScope = CoroutineScope(
            resolvedRuntime.scope.coroutineContext +
                Dispatchers.Default.limitedParallelism(1) +
                CoroutineName("ComputerHandler")
        )
        mailboxSignal = Channel(Channel.CONFLATED)
        active = true
        started = true
        processorJob = serviceScope?.launch {
            processLoop()
        }
        Logger.info("ComputerHandler started")
        signalMailbox()

        synchronized(computerLock) {
            computerList.data.forEach { raw ->
                (raw as? Computer)?.start(resolvedRuntime)
            }
        }
    }

    override fun start() {
        start(runtime)
    }

    override fun shutdown() {
        active = false
        mailboxSignal.close()
        processorJob?.cancel()
        synchronized(computerLock) {
            computerList.data.forEach { raw ->
                (raw as? Computer)?.shutdown()
            }
        }
    }

    override suspend fun join() {
        processorJob?.join()
        synchronized(computerLock) {
            computerList.data.forEach { raw ->
                (raw as? Computer)?.joinBlocking()
            }
        }
        if (ownsRuntime) {
            runtime?.close()
            ownsRuntime = false
        }
        started = false
    }

    fun joinBlocking() {
        runBlocking {
            join()
        }
    }

    fun broadcast(AD: ApplicationData) {
        synchronized(computerLock) {
            computerList.data.forEach { raw ->
                (raw as? Computer)?.addData(AD)
            }
        }
    }

    fun startCountDown() {
        on = false
        synchronized(computerLock) {
            computerList.data.forEach { raw ->
                (raw as? Computer)?.startCountDown()
            }
        }
    }

    fun addData(applicationData: Any?, ip: String?, source: Int) {
        val resolved = (applicationData as? ApplicationData)?.withSource(source) ?: applicationData
        addData(resolved, ip)
    }

    fun addData(applicationData: Any?, ip: String?) {
        synchronized(taskLock) {
            mailboxTasks.addLast(ApplicationDataTask(applicationData, ip))
        }
        Logger.debug("Queued handler task for ip={} payloadType={}", ip, applicationData?.javaClass?.simpleName)
        signalMailbox()
    }

    fun addComputer(computer: Computer) {
        synchronized(computerLock) {
            computerList.add(computer)
        }
        failedRemoteLoads.remove(computer.ip)
        Logger.info("Registered computer ip={}", computer.ip)
        runtime.takeIf { started }?.let { computer.start(it) }
    }

    fun incrementPlayers() {
        playerCount++
    }

    fun getPlayers(): Int {
        return playerCount
    }

    fun decrementPlayers() {
        playerCount--
    }

    fun getComputer(ip: String?): Computer? {
        synchronized(computerLock) {
            return computerList.get(ip) as? Computer
        }
    }

    private fun signalMailbox() {
        mailboxSignal.trySend(Unit)
    }

    private fun pollTask(): ApplicationDataTask? {
        synchronized(taskLock) {
            return if (mailboxTasks.isEmpty()) null else mailboxTasks.removeFirst()
        }
    }

    private suspend fun processLoop() {
        while (active) {
            mailboxSignal.receiveCatching().getOrNull() ?: break
            while (true) {
                val task = pollTask() ?: break
                runCatching {
                    processTask(task)
                }.onFailure(Throwable::printStackTrace)
            }
        }
    }

    private fun processTask(task: ApplicationDataTask) {
        val routedIp = resolveTaskIp(task.ip)
        if (task.applicationData == null) {
            Logger.info("Unloading player ip={} routedIp={}", task.ip, routedIp)
            val computer = synchronized(computerLock) {
                val loaded = computerList.get(routedIp) as? Computer
                computerList.remove(routedIp)
                loaded
            }
            rememberFailedRemoteLoad(routedIp, computer)
            serverBridge?.removeRandomKey(routedIp)
            computer?.setRun(false)
            return
        }

        val current = synchronized(computerLock) {
            computerList.get(routedIp) as? Computer
        }
        if (current != null) {
            if (current.getLoaded()) {
                Logger.debug("Dispatching task directly to loaded computer ip={} routedIp={}", task.ip, routedIp)
                current.addData(task.applicationData)
            } else {
                Logger.debug("Computer ip={} routedIp={} still loading; queueing task on computer", task.ip, routedIp)
                if (task.applicationData is ApplicationData) {
                    current.addData(task.applicationData)
                } else {
                    Logger.debug(
                        "Dropping non-application task for loading computer ip={} routedIp={} payloadType={}",
                        task.ip,
                        routedIp,
                        task.applicationData?.javaClass?.simpleName ?: "null"
                    )
                }
            }
            return
        }

        if (!on || routedIp == null) {
            return
        }

        findFailedRemoteLoad(routedIp)?.let { failedLoad ->
            if (dispatchFailedRemoteLoad(task, routedIp, failedLoad)) {
                Logger.debug("Short-circuited cached failed remote load for ip={}", routedIp)
                return
            }
        }

        if (isUnresolvedEncryptedToken(task.ip, routedIp)) {
            Logger.warn(
                "Dropping handler task for unresolved encrypted ip token={} payloadType={}",
                task.ip,
                task.applicationData?.javaClass?.simpleName
            )
            return
        }

        val computer = Computer(routedIp, this, -1, serverBridge)
        if (task.applicationData is ApplicationData) {
            computer.loadRequester = task.applicationData.sourceIP
            computer.addData(task.applicationData)
        }
        Logger.info("Created computer on demand for ip={} routedIp={} via handler task", task.ip, routedIp)
        addComputer(computer)
        computer.loadSave()
    }

    private fun rememberFailedRemoteLoad(ip: String?, computer: Computer?) {
        if (ip == null || computer == null || !computer.LOAD_FAILURE || computer.connectionID != -1) {
            return
        }
        failedRemoteLoads[ip] = FailedRemoteLoad(
            errorMessage = computer.errorMessage,
            expiresAtMillis = System.currentTimeMillis() + FAILED_REMOTE_LOAD_CACHE_MS
        )
    }

    private fun findFailedRemoteLoad(ip: String): FailedRemoteLoad? {
        val cached = failedRemoteLoads[ip] ?: return null
        if (cached.expiresAtMillis <= System.currentTimeMillis()) {
            failedRemoteLoads.remove(ip)
            return null
        }
        return cached
    }

    private fun dispatchFailedRemoteLoad(task: ApplicationDataTask, routedIp: String, failedLoad: FailedRemoteLoad): Boolean {
        val applicationData = task.applicationData as? ApplicationData ?: return false
        val requesterIp = applicationData.sourceIP.takeIf { it.isNotBlank() } ?: return true

        when (applicationData.command) {
            com.hackwars.rpc.GameCommands.PETTYCASH.command -> {
                addData(applicationData, requesterIp)
            }

            com.hackwars.rpc.GameCommands.REQUESTWEBPAGE.command -> {
                addData(
                    ApplicationData(
                        WebPagePayload(
                            SaveLogoutTickService.LOAD_FAILURE_WEBPAGE_TITLE,
                            SaveLogoutTickService.LOAD_FAILURE_WEBPAGE_BODY,
                            null,
                            0,
                        ),
                        0,
                        routedIp
                    ),
                    requesterIp
                )
            }
        }

        if (failedLoad.errorMessage.isNotBlank()) {
            addData(
                ApplicationData(MessageTextPayload(failedLoad.errorMessage), 0, routedIp),
                requesterIp
            )
        }
        return true
    }

    private fun resolveTaskIp(ip: String?): String? {
        if (ip == null) {
            return null
        }
        val resolved = serverBridge?.resolveEncryptedIp(ip)
        if (resolved != null && resolved != ip) {
            Logger.info("Resolved encrypted handler ip {} to {}", ip, resolved)
        }
        return resolved ?: ip
    }

    private fun isUnresolvedEncryptedToken(originalIp: String?, routedIp: String): Boolean {
        return originalIp != null &&
            originalIp == routedIp &&
            originalIp.length == 10 &&
            originalIp.all { it in 'a'..'z' }
    }
}
