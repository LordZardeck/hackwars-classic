package hackersearch.util

import com.plink.dolphinstem.ItemData
import com.plink.dolphinstem.TextSource
import com.plink.dolphinstem.WordData
import hackersearch.assignments.SearchAssignment
import hackersearch.assignments.SearchResult
import hackersearch.assignments.SearchResultAssignment
import hackersearch.server.SearchServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import org.w3c.dom.Node
import server.runtime.GameServerRuntime
import server.runtime.GameServerService
import util.LoadXML
import util.sql
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

open class SearchHandler(
    private var myServer: SearchServer?,
    runtime: GameServerRuntime? = null
) : GameServerService {
    private val Connection = "127.0.0.1"
    private val DB = "hackwars"
    private val Username = "root"
    private val Password = ""
    private val ownsRuntime = runtime == null
    private val runtime: GameServerRuntime = runtime ?: GameServerRuntime()
    private val commands = Channel<SearchCommand>(Channel.UNLIMITED)
    private val started = AtomicBoolean(false)
    private val shutdownRequested = AtomicBoolean(false)
    private val pendingOperations = AtomicInteger(0)
    private var workerJob: Job? = null
    private var snapshot = SearchSnapshot()

    fun bindServer(server: SearchServer?) {
        myServer = server
    }

    override fun start() {
        start(true)
    }

    fun start(primeFromDatabase: Boolean = true) {
        if (!started.compareAndSet(false, true)) {
            return
        }

        if (primeFromDatabase) {
            bootstrapFromDatabase()
        }

        workerJob = runtime.serialScope("SearchHandler").launch {
            processCommands()
        }
    }

    override fun shutdown() {
        if (!shutdownRequested.compareAndSet(false, true)) {
            return
        }

        commands.close()
    }

    override suspend fun join() {
        workerJob?.join()
        if (ownsRuntime) {
            runtime.close()
        }
    }

    suspend fun awaitIdle() {
        if (!started.get() || shutdownRequested.get()) {
            return
        }

        val barrier = CompletableDeferred<Unit>()
        commands.send(SearchCommand.Barrier(barrier))
        barrier.await()
    }

    fun requestSearch(mySearchAssignment: SearchAssignment): SearchResultAssignment {
        val currentSnapshot = snapshot
        val searchTerms = mySearchAssignment.getVector()
            ?.getData()
            ?.mapNotNull { it as? WordData }
            ?.mapNotNull { word -> word.getData()?.toString()?.lowercase() }
            .orEmpty()

        val returnMe = SearchResultAssignment(mySearchAssignment.getID())
        searchTerms.forEach(returnMe::addSearchTerm)

        if (searchTerms.isEmpty()) {
            returnMe.setCurrent(mySearchAssignment.getIndex())
            returnMe.setSize(0)
            return returnMe
        }

        val ranked = currentSnapshot.pagesByAddress.values
            .mapNotNull { page ->
                val score = page.score(searchTerms)
                if (score > 0.0) {
                    page to score
                } else {
                    null
                }
            }
            .sortedWith(
                compareByDescending<Pair<IndexedPage, Double>> { it.second }
                    .thenBy { it.first.address }
            )

        val startIndex = mySearchAssignment.getIndex().coerceAtLeast(0)
        if (startIndex >= ranked.size) {
            returnMe.setCurrent(startIndex)
            returnMe.setSize(ranked.size)
            return returnMe
        }

        val upperBound = min(startIndex + ReturnCount, ranked.size)
        for (i in startIndex until upperBound) {
            val page = ranked[i].first
            val result = SearchResult()
            result.setAddress(page.address)
            result.setTitle(page.title)
            result.setDescription(page.description)
            returnMe.addResult(result)
        }

        returnMe.setCurrent(startIndex)
        returnMe.setSize(ranked.size)
        return returnMe
    }

    fun indexPage(title: String, address: String, content: String) {
        if (shutdownRequested.get()) {
            return
        }

        if (!started.get()) {
            applyIndexedPage(title, address, content)
            return
        }

        pendingOperations.incrementAndGet()
        if (!commands.trySend(SearchCommand.IndexPage(title, address, content)).isSuccess) {
            pendingOperations.decrementAndGet()
            applyIndexedPage(title, address, content)
        }
    }

    fun getLoaded(): Boolean {
        return started.get() && pendingOperations.get() == 0
    }

    private suspend fun processCommands() {
        for (command in commands) {
            when (command) {
                is SearchCommand.IndexPage -> {
                    runCatching {
                        applyIndexedPage(command.title, command.address, command.content)
                    }.onFailure {
                        it.printStackTrace()
                    }
                    pendingOperations.decrementAndGet()
                }
                is SearchCommand.Barrier -> {
                    command.completion.complete(Unit)
                }
            }
        }
    }

    private fun applyIndexedPage(title: String?, address: String?, content: String?) {
        val safeAddress = address?.trim().orEmpty()
        if (safeAddress.isEmpty()) {
            return
        }

        val sanitizedTitle = sanitizeText(title ?: "")
        val sanitizedContent = sanitizeText(content ?: "")
        val normalizedContent = sanitizedContent.lowercase()
        val terms = extractTerms(normalizedContent)
        val description = if (sanitizedContent.length >= 128) {
            sanitizedContent.substring(0, 128) + "..."
        } else {
            sanitizedContent
        }

        snapshot = snapshot.withIndexedPage(
            IndexedPage(
                title = sanitizedTitle,
                address = safeAddress,
                description = description,
                termWeights = terms
            )
        )
    }

    private fun extractTerms(content: String): Map<String, Double> {
        if (content.isBlank()) {
            return emptyMap()
        }

        return runCatching {
            val primeMe = ArrayList<Any?>()
            primeMe.add(content)
            val prime = TextSource("source", primeMe)
            prime.setStopWordFile(STOP_WORD_LOCATION)
            prime.prime()
            val temp = prime.getItems()
            val vector = if (temp != null && temp.isNotEmpty()) {
                (temp[0] as ItemData).getVectorData()
            } else {
                null
            }

            val terms = mutableMapOf<String, Double>()
            vector?.getData()?.forEach { item ->
                val word = item as? WordData ?: return@forEach
                val token = word.getData()?.toString()?.lowercase().orEmpty()
                if (token.isNotEmpty()) {
                    terms[token] = (terms[token] ?: 0.0) + word.getFrequency().toDouble()
                }
            }
            terms
        }.getOrElse {
            emptyMap()
        }
    }

    private fun bootstrapFromDatabase() {
        try {
            val c = sql(Connection, DB, Username, Password)
            try {
                val result = c.process("select max(num) from user;")
                val fileCount = result?.firstOrNull()?.toString()?.toIntOrNull() ?: 0

                var indexedCount = 0
                for (num in 1..fileCount) {
                    val dataResult = c.process("select stats,ip from user where num=$num;")
                    val data = dataResult?.getOrNull(0)?.toString().orEmpty()
                    val ip = dataResult?.getOrNull(1)?.toString().orEmpty()
                    if (data.isBlank() || ip.isBlank()) {
                        continue
                    }

                    val metaResult = c.process(
                        "SELECT TO_DAYS(NOW())-TO_DAYS(last_logged_in),npc FROM hackerforum.users WHERE ip='$ip'"
                    )
                    val isActive = metaResult?.getOrNull(0)?.toString()?.toIntOrNull() ?: 0
                    val isNpc = metaResult?.getOrNull(1)?.toString() == "Y"
                    if (isActive < 14 && !isNpc) {
                        continue
                    }

                    val loaded = loadBootstrapPage(data)
                    if (loaded != null) {
                        applyIndexedPage(loaded.title, loaded.address, loaded.content)
                        indexedCount++
                    }
                }

                snapshot = snapshot.copy(fileCount = fileCount, indexedCount = indexedCount)
            } finally {
                c.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadBootstrapPage(data: String): BootstrapPage? {
        return runCatching {
            val loader = LoadXML()
            loader.loadByteArray(data.toByteArray())

            var address = ""
            var title = ""
            var content = ""

            var node: Node? = loader.findNodeRecursive("ip", 0)
            node = loader.findNodeRecursive(node, "#text", 0)
            if (node != null) {
                address = node.nodeValue
            }

            node = loader.findNodeRecursive("title", 0)
            node = loader.findNodeRecursive(node, "#text", 0)
            if (node != null) {
                title = node.nodeValue
            }

            node = loader.findNodeRecursive("body", 0)
            node = loader.findNodeRecursive(node, "#text", 0)
            if (node != null) {
                content = node.nodeValue
            }

            content = sanitizeText(content).lowercase()
            BootstrapPage(title, address, content)
        }.getOrNull()
    }

    private fun sanitizeText(value: String): String {
        return value
            .replace(Regex("\\<.*?\\>"), "")
            .replace(Regex("\\&.*?;"), "")
            .replace(Regex("\\n"), "")
    }

    private sealed class SearchCommand {
        data class IndexPage(val title: String?, val address: String?, val content: String?) : SearchCommand()
        data class Barrier(val completion: CompletableDeferred<Unit>) : SearchCommand()
    }

    private data class IndexedPage(
        val title: String,
        val address: String,
        val description: String,
        val termWeights: Map<String, Double>
    ) {
        fun score(queryTerms: List<String>): Double {
            if (termWeights.isEmpty() || queryTerms.isEmpty()) {
                return 0.0
            }

            return queryTerms.sumOf { term -> termWeights[term] ?: 0.0 }
        }
    }

    private data class SearchSnapshot(
        val pagesByAddress: Map<String, IndexedPage> = emptyMap(),
        val fileCount: Int = 0,
        val indexedCount: Int = 0
    ) {
        fun withIndexedPage(page: IndexedPage): SearchSnapshot {
            val nextPages = HashMap(pagesByAddress)
            val isNew = nextPages.put(page.address, page) == null
            return copy(
                pagesByAddress = nextPages,
                indexedCount = if (isNew) indexedCount + 1 else indexedCount
            )
        }
    }

    private data class BootstrapPage(
        val title: String,
        val address: String,
        val content: String
    )

    companion object {
        const val STOP_WORD_LOCATION = "stop_words.txt"
        private const val ReturnCount = 10

        @JvmField
        var MySearchHandler: SearchHandler? = null

        @JvmStatic
        fun getInstance(myServer: SearchServer?, runtime: GameServerRuntime? = null): SearchHandler {
            if (MySearchHandler == null) {
                MySearchHandler = SearchHandler(myServer, runtime)
            } else {
                MySearchHandler!!.bindServer(myServer)
            }

            MySearchHandler!!.start()
            return MySearchHandler!!
        }

        @JvmStatic
        fun resetForTests() {
            MySearchHandler = null
        }
    }
}
