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
import util.Time

/**
 * A central location for distributing messages to other computers.
 */
class ComputerHandler @JvmOverloads constructor(
    private val MyTime: Time?,
    private val MyHackerServer: HackerServerBridge?,
    private var runtime: GameServerRuntime? = null
) : GameServerService {
    companion object {
        private val Logger = LoggerFactory.getLogger(ComputerHandler::class.java)
    }

    private val Computers = ComputerBinaryList()
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
            Computers.data.forEach { raw ->
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
            Computers.data.forEach { raw ->
                (raw as? Computer)?.shutdown()
            }
        }
    }

    override suspend fun join() {
        processorJob?.join()
        synchronized(computerLock) {
            Computers.data.forEach { raw ->
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
            Computers.data.forEach { raw ->
                (raw as? Computer)?.addData(AD)
            }
        }
    }

    fun startCountDown() {
        on = false
        synchronized(computerLock) {
            Computers.data.forEach { raw ->
                (raw as? Computer)?.startCountDown()
            }
        }
    }

    fun addData(applicationData: Any?, ip: String?, source: Int) {
        (applicationData as? ApplicationData)?.source = source
        addData(applicationData, ip)
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
            Computers.add(computer)
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
            return Computers.get(ip) as? Computer
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
        if (task.applicationData == null) {
            Logger.info("Unloading player ip={}", task.ip)
            val computer = synchronized(computerLock) {
                val loaded = Computers.get(task.ip) as? Computer
                Computers.remove(task.ip)
                loaded
            }
            MyHackerServer?.removeRandomKey(task.ip)
            computer?.setRun(false)
            return
        }

        val current = synchronized(computerLock) {
            Computers.get(task.ip) as? Computer
        }
        if (current != null) {
            if (current.getLoaded()) {
                Logger.debug("Dispatching task directly to loaded computer ip={}", task.ip)
                current.addData(task.applicationData)
            } else {
                Logger.debug("Computer ip={} still loading; requeueing task", task.ip)
                addData(task.applicationData, task.ip)
            }
            return
        }

        if (!on || task.ip == null) {
            return
        }

        val computer = Computer(task.ip, this, MyTime ?: Time(), -1, MyHackerServer)
        if (task.applicationData is ApplicationData) {
            computer.setLoadRequester(task.applicationData.sourceIP)
            computer.addData(task.applicationData)
        }
        Logger.info("Created computer on demand for ip={} via handler task", task.ip)
        addComputer(computer)
        computer.loadSave()
    }
}
