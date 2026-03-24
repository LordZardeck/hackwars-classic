package game
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
    }

    private val computerList = ComputerBinaryList()
    private val taskLock = Any()
    private val computerLock = Any()
    private val mailboxTasks = ArrayDeque<ApplicationDataTask>()
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
                Logger.debug("Computer ip={} routedIp={} still loading; requeueing task", task.ip, routedIp)
                addData(task.applicationData, routedIp)
            }
            return
        }

        if (!on || routedIp == null) {
            return
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
