package com.hackwars.rewrite.gamecore

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.time.Duration.Companion.days

@Serializable
data class RequestSearchPayload(
    val query: String? = null,
    val startIndex: Int = 0,
)

class RequestSearchCommand(
    private val requesterStateId: GameStateId,
    private val query: String,
    private val startIndex: Int,
    private val searchCatalogRepository: SearchCatalogRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<SearchResultsResponse> {
    override val name: String = "requestsearch"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(requesterStateId)

    override suspend fun execute(context: CommandContext): SearchResultsResponse {
        val normalizedStartIndex = max(0, startIndex)
        val queryTerms = tokenizeSearchTerms(query)
        if (queryTerms.isEmpty()) {
            return SearchResultsResponse(
                queryTerms = emptyList(),
                startIndex = normalizedStartIndex,
                totalSize = 0,
                results = emptyList(),
            )
        }

        val now = clock()
        val ranked = searchCatalogRepository.loadDocuments()
            .asSequence()
            .filter { it.isVisibleForSearch(now) }
            .mapNotNull { document ->
                val sanitizedBody = sanitizeSearchText(document.body)
                val score = scoreSearchDocument(sanitizedBody, queryTerms)
                if (score <= 0) {
                    null
                } else {
                    SearchRanking(
                        entry = SearchResultEntry(
                            address = document.address.trim().lowercase(),
                            title = sanitizeSearchText(document.title),
                            description = buildSearchDescription(sanitizedBody),
                        ),
                        score = score,
                    )
                }
            }
            .sortedWith(
                compareByDescending<SearchRanking> { it.score }
                    .thenBy { it.entry.address },
            )
            .map(SearchRanking::entry)
            .toList()

        val pageResults = if (normalizedStartIndex >= ranked.size) {
            emptyList()
        } else {
            ranked.subList(
                normalizedStartIndex,
                minOf(normalizedStartIndex + SEARCH_PAGE_SIZE, ranked.size),
            )
        }

        return SearchResultsResponse(
            queryTerms = queryTerms,
            startIndex = normalizedStartIndex,
            totalSize = ranked.size,
            results = pageResults,
        )
    }
}

class InMemorySearchCatalogRepository(
    private val documents: List<SearchableWebsiteDocument>,
) : SearchCatalogRepository {
    override suspend fun loadDocuments(): List<SearchableWebsiteDocument> = documents
}

internal fun ComputerState.toSearchableWebsiteDocument(): SearchableWebsiteDocument {
    return SearchableWebsiteDocument(
        address = identity.playerIp.trim().lowercase(),
        title = website.title,
        body = website.body,
        searchable = ports.any { port ->
            port.defaultPort &&
                port.enabled &&
                port.installedApplication?.kind == ApplicationKind.HTTP
        },
        lastLoginAtEpochMillis = identity.lastLoginAtEpochMillis,
        isNpc = identity.isNpc,
    )
}

internal fun sanitizeSearchText(value: String): String {
    return value
        .replace(Regex("\\<.*?\\>"), "")
        .replace(Regex("\\&.*?;"), "")
        .replace("\n", "")
        .replace("\r", "")
        .trim()
}

private fun tokenizeSearchTerms(query: String): List<String> {
    val stopWords = searchStopWords()
    return sanitizeSearchText(query)
        .lowercase()
        .split(Regex("[^a-z0-9']+"))
        .filter { token -> token.isNotBlank() && token !in stopWords }
}

private fun scoreSearchDocument(
    sanitizedBody: String,
    queryTerms: List<String>,
): Double {
    if (sanitizedBody.isBlank() || queryTerms.isEmpty()) {
        return 0.0
    }
    val weights = mutableMapOf<String, Double>()
    sanitizedBody.lowercase()
        .split(Regex("[^a-z0-9']+"))
        .filter(String::isNotBlank)
        .forEach { token ->
            weights[token] = (weights[token] ?: 0.0) + 1.0
        }
    return queryTerms.sumOf { term -> weights[term] ?: 0.0 }
}

private fun buildSearchDescription(sanitizedBody: String): String {
    return if (sanitizedBody.length > SEARCH_DESCRIPTION_LENGTH) {
        sanitizedBody.take(SEARCH_DESCRIPTION_LENGTH) + "..."
    } else {
        sanitizedBody
    }
}

private fun SearchableWebsiteDocument.isVisibleForSearch(now: Long): Boolean {
    if (!searchable) {
        return false
    }
    if (isNpc) {
        return true
    }
    val lastLogin = lastLoginAtEpochMillis ?: return false
    return now - lastLogin >= SEARCH_INACTIVE_THRESHOLD_MS
}

private data class SearchRanking(
    val entry: SearchResultEntry,
    val score: Double,
)

private fun searchStopWords(): Set<String> {
    return SearchStopWords.words
}

private object SearchStopWords {
    val words: Set<String> by lazy {
        val contents = SearchStopWords::class.java.classLoader
            .getResourceAsStream("stop_words.txt")
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        contents
            .lowercase()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .toSet()
    }
}

private const val SEARCH_PAGE_SIZE: Int = 10
private const val SEARCH_DESCRIPTION_LENGTH: Int = 128
private val SEARCH_INACTIVE_THRESHOLD_MS: Long = 14.days.inWholeMilliseconds
