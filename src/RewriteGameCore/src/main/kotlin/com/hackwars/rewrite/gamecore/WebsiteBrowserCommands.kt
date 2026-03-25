package com.hackwars.rewrite.gamecore

import com.hackwars.rewrite.hackscript.AppendHostLog
import com.hackwars.rewrite.hackscript.HttpHookEffect
import com.hackwars.rewrite.hackscript.HttpHookExecutionResult
import com.hackwars.rewrite.hackscript.PopupToVisitor
import com.hackwars.rewrite.hackscript.TriggerLocalWatch
import com.hackwars.rewrite.hackscript.TriggerRemoteWatch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.Serializable

const val LEGACY_SERVER_NOT_FOUND_TITLE: String = "Server Not Found"
const val LEGACY_SERVER_NOT_FOUND_BODY: String =
    "<html><head><title>Hack Wars - Error report</title><style><!--H1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;color:white} H2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} H3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} BODY {background-color:rgb(0,0,0);font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;color:white;} B {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;color:white;} P {color:white;font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;}A {color : black;}A.name {color : black;}HR {color : #525D76;}--></style> </head><body><h1 style=\"width:100%\">HTTP Status 408</h1><HR size=\"1\" noshade=\"noshade\"><p style=\"background-color:black;\"><b>type</b> HTTP Error</p><p style=\"background-color:black;\"><b>message</b> <u>Resource not found.</u></p><p style=\"background-color:black\"><b>description</b> <u>The HTTP server of the player you attempted to connect to does not seem to be on.</u></p><HR size=\"1\" noshade=\"noshade\"><h3>&copy; Hack Wars</h3></body></html>"

interface HttpHookRuntime {
    suspend fun onEnter(request: HttpHookRequest): HttpHookExecutionResult? = null

    suspend fun onSubmit(request: HttpHookRequest): HttpHookExecutionResult? = null

    suspend fun onExit(request: HttpHookRequest): HttpHookExecutionResult? = null
}

data class HttpHookRequest(
    val sourceStateId: GameStateId,
    val targetStateId: GameStateId,
    val queryParameters: Map<String, String>,
    val formParameters: Map<String, String>,
    val targetState: ComputerState,
    val installedApplication: InstalledApplication,
)

object NoOpHttpHookRuntime : HttpHookRuntime

@Serializable
data class RequestPagePayload(
    val ip: String,
)

@Serializable
data class SavePagePayload(
    val ip: String,
    val title: String? = null,
    val body: String? = null,
)

@Serializable
data class RequestWebpagePayload(
    val targetIp: String,
    val sourceIp: String,
    val parameters: Map<String, String> = emptyMap(),
)

@Serializable
data class SubmitWebpagePayload(
    val targetIp: String? = null,
    val sourceIp: String,
    val parameters: Map<String, String> = emptyMap(),
)

@Serializable
data class ExitWebpagePayload(
    val targetIp: String? = null,
    val sourceIp: String,
)

@Serializable
data class VotePayload(
    val targetIp: String? = null,
    val sourceIp: String,
)

class RequestPageCommand(
    private val stateId: GameStateId,
) : RequestCommand<PageEditorResponse> {
    override val name: String = "requestpage"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): PageEditorResponse {
        val state = context.loadState(stateId) ?: ComputerState.empty(stateId, playerIp = stateId.value)
        return PageEditorResponse(
            stateId = stateId,
            title = state.website.title,
            body = state.website.body,
            version = state.version,
        )
    }
}

class SavePageCommand(
    private val stateId: GameStateId,
    private val title: String,
    private val body: String,
) : RequestCommand<SavePageResponse> {
    override val name: String = "savepage"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(stateId)

    override suspend fun execute(context: CommandContext): SavePageResponse {
        require(body.length <= 30_000) { "Website body exceeds the 30000 character limit." }
        val updated = context.appendEvents(
            id = stateId,
            events = listOf(
                WebsiteSavedEvent(
                    title = title,
                    body = body,
                ),
            ),
        )
        return SavePageResponse(
            stateId = stateId,
            title = updated.website.title,
            body = updated.website.body,
            version = updated.version,
        )
    }
}

class RequestWebpageCommand(
    private val sourceStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val parameters: Map<String, String>,
    private val httpHookRuntime: HttpHookRuntime = NoOpHttpHookRuntime,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<WebsiteRenderResponse> {
    override val name: String = "requestwebpage"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(sourceStateId, targetStateId)

    override suspend fun execute(context: CommandContext): WebsiteRenderResponse {
        val targetState = context.loadState(targetStateId) ?: return fallbackWebsite(targetStateId)
        val installedApplication = targetState.activeDefaultApplication(ApplicationKind.HTTP)
            ?: return fallbackWebsite(targetStateId, targetState.version)
        if (!targetState.hasActiveDefaultApplicationPort(ApplicationKind.HTTP)) {
            return fallbackWebsite(targetStateId, targetState.version)
        }

        val executionResult = httpHookRuntime.onEnter(
            HttpHookRequest(
                sourceStateId = sourceStateId,
                targetStateId = targetStateId,
                queryParameters = parameters,
                formParameters = emptyMap(),
                targetState = targetState,
                installedApplication = installedApplication,
            ),
        )
        val updatedTargetState = processHookEffects(
            originCommandName = name,
            context = context,
            sourceStateId = sourceStateId,
            targetState = targetState,
            effects = executionResult?.effects.orEmpty(),
            clock = clock,
        )
        return renderWebsite(updatedTargetState, executionResult)
    }
}

class SubmitWebpageCommand(
    private val sourceStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val parameters: Map<String, String>,
    private val httpHookRuntime: HttpHookRuntime = NoOpHttpHookRuntime,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<WebsiteRenderResponse> {
    override val name: String = "submit"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(sourceStateId, targetStateId)

    override suspend fun execute(context: CommandContext): WebsiteRenderResponse {
        val targetState = context.loadState(targetStateId) ?: return fallbackWebsite(targetStateId)
        val installedApplication = targetState.activeDefaultApplication(ApplicationKind.HTTP)
            ?: return fallbackWebsite(targetStateId, targetState.version)
        if (!targetState.hasActiveDefaultApplicationPort(ApplicationKind.HTTP)) {
            return fallbackWebsite(targetStateId, targetState.version)
        }

        val executionResult = httpHookRuntime.onSubmit(
            HttpHookRequest(
                sourceStateId = sourceStateId,
                targetStateId = targetStateId,
                queryParameters = emptyMap(),
                formParameters = parameters,
                targetState = targetState,
                installedApplication = installedApplication,
            ),
        )
        val updatedTargetState = processHookEffects(
            originCommandName = name,
            context = context,
            sourceStateId = sourceStateId,
            targetState = targetState,
            effects = executionResult?.effects.orEmpty(),
            clock = clock,
        )
        return renderWebsite(updatedTargetState, executionResult)
    }
}

class ExitWebpageCommand(
    private val sourceStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val httpHookRuntime: HttpHookRuntime = NoOpHttpHookRuntime,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : FireAndForgetCommand {
    override val name: String = "exit"
    override val lifetime: CommandLifetime = CommandLifetime.defaultFireAndForget
    override val targetStateIds: Set<GameStateId> = setOf(sourceStateId, targetStateId)

    override suspend fun execute(context: CommandContext) {
        val targetState = context.loadState(targetStateId) ?: return
        val installedApplication = targetState.activeDefaultApplication(ApplicationKind.HTTP) ?: return
        val executionResult = httpHookRuntime.onExit(
            HttpHookRequest(
                sourceStateId = sourceStateId,
                targetStateId = targetStateId,
                queryParameters = emptyMap(),
                formParameters = emptyMap(),
                targetState = targetState,
                installedApplication = installedApplication,
            ),
        )
        processHookEffects(
            originCommandName = name,
            context = context,
            sourceStateId = sourceStateId,
            targetState = targetState,
            effects = executionResult?.effects.orEmpty(),
            clock = clock,
        )
    }
}

class VoteForWebsiteCommand(
    private val voterStateId: GameStateId,
    private val targetStateId: GameStateId,
) : RequestCommand<VoteResponse> {
    override val name: String = "vote"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(voterStateId, targetStateId)

    override suspend fun execute(context: CommandContext): VoteResponse {
        val states = context.loadStates(targetStateIds)
        val voterState = requireNotNull(states[voterStateId]) {
            "No voter state exists for ${voterStateId.value}."
        }
        val targetState = requireNotNull(states[targetStateId]) {
            "No website target state exists for ${targetStateId.value}."
        }

        require(voterState.stats.totalLevel >= voterState.stats.noobProtectionLevel) {
            "Voter ${voterStateId.value} is below the noob-protection voting threshold."
        }
        require(voterStateId != targetStateId) {
            "Players cannot vote for their own website."
        }
        require(voterState.website.votesAvailable > 0) {
            "No website votes are currently available."
        }
        require(targetState.hasActiveDefaultApplicationPort(ApplicationKind.HTTP)) {
            "Target ${targetStateId.value} does not have an active default HTTP site."
        }

        val updatedVoter = context.appendEvents(
            id = voterStateId,
            events = listOf(WebsiteVotesAvailableAdjustedEvent(delta = -1)),
        )
        val updatedTarget = context.appendEvents(
            id = targetStateId,
            events = listOf(
                WebsiteVoteCountAdjustedEvent(delta = 1),
                HttpExperienceAdjustedEvent(delta = 500),
            ),
        )

        return VoteResponse(
            voterStateId = voterStateId,
            targetStateId = targetStateId,
            votesAvailableAfter = updatedVoter.website.votesAvailable,
            targetVoteCountAfter = updatedTarget.website.voteCount,
            targetHttpExperienceAfter = updatedTarget.stats.experienceByFamily[ScriptFamily.HTTP] ?: 0,
            voterVersion = updatedVoter.version,
            targetVersion = updatedTarget.version,
        )
    }
}

private fun renderWebsite(
    targetState: ComputerState,
    executionResult: HttpHookExecutionResult? = null,
): WebsiteRenderResponse {
    val includeStore = executionResult?.includeStore ?: true
    return WebsiteRenderResponse(
        resolvedTargetStateId = targetState.id,
        title = targetState.website.title,
        body = executionResult?.body ?: targetState.website.body,
        storeFiles = if (includeStore && targetState.canRenderStoreListing()) {
            targetState.filesystem.listDirectory("/Store").files
        } else {
            emptyList()
        },
        fallback = false,
        version = targetState.version,
    )
}

private suspend fun processHookEffects(
    originCommandName: String,
    context: CommandContext,
    sourceStateId: GameStateId,
    targetState: ComputerState,
    effects: List<HttpHookEffect>,
    clock: () -> Long,
): ComputerState {
    var currentTargetState = targetState
    effects.forEach { effect ->
        when (effect) {
            is AppendHostLog -> {
                val createdAt = clock()
                currentTargetState = context.appendEvents(
                    id = targetState.id,
                    events = listOf(
                        HostLogAppendedEvent(
                            entry = ComputerLogEntry(
                                createdAtEpochMillis = createdAt,
                                renderedLine = renderLegacyLogLine(createdAt, effect.message),
                                sourceIp = sourceStateId.value,
                            ),
                        ),
                    ),
                )
            }

            is PopupToVisitor -> {
                context.publishUiEvent(PopupUiEvent(effect.message))
            }

            is TriggerLocalWatch -> {
                context.emitWatchTrigger(
                    WatchTriggerIntent(
                        targetStateId = targetState.id,
                        selector = TriggerSelector.ByIndex(effect.index),
                        sourceIp = sourceStateId.value,
                        parameters = effect.parameters,
                        external = true,
                        originCommandName = originCommandName,
                        requestId = context.requestId,
                    ),
                )
            }

            is TriggerRemoteWatch -> {
                context.emitWatchTrigger(
                    WatchTriggerIntent(
                        targetStateId = GameStateId(effect.targetIp),
                        selector = TriggerSelector.ByIndex(effect.index),
                        sourceIp = sourceStateId.value,
                        parameters = effect.parameters,
                        external = true,
                        originCommandName = originCommandName,
                        requestId = context.requestId,
                    ),
                )
            }
        }
    }
    return currentTargetState
}

internal fun renderLegacyLogLine(
    createdAtEpochMillis: Long,
    message: String,
): String {
    val timestamp = Instant.ofEpochMilli(createdAtEpochMillis)
        .atZone(ZoneId.systemDefault())
        .format(LEGACY_LOG_FORMATTER)
    return "$timestamp $message"
}

private val LEGACY_LOG_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d-MMM-yyyy (h:mm:ss a)", Locale.US)

private fun fallbackWebsite(
    targetStateId: GameStateId,
    version: Long = 0,
): WebsiteRenderResponse {
    return WebsiteRenderResponse(
        resolvedTargetStateId = targetStateId,
        title = LEGACY_SERVER_NOT_FOUND_TITLE,
        body = LEGACY_SERVER_NOT_FOUND_BODY,
        storeFiles = emptyList(),
        fallback = true,
        version = version,
    )
}

private fun ComputerState.canRenderStoreListing(): Boolean {
    return hasActiveDefaultBankPort() && hasActiveDefaultApplicationPort(ApplicationKind.FTP)
}

private fun ComputerState.hasActiveDefaultApplicationPort(kind: ApplicationKind): Boolean {
    return ports.any { port ->
        port.defaultPort &&
            port.enabled &&
            port.installedApplication?.kind == kind
    }
}

private fun ComputerState.activeDefaultApplication(kind: ApplicationKind): InstalledApplication? {
    return ports.firstOrNull { port ->
        port.defaultPort &&
            port.enabled &&
            port.installedApplication?.kind == kind
    }?.installedApplication
}
