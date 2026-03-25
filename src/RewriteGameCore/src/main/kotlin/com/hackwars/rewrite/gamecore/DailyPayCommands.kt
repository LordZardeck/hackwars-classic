package com.hackwars.rewrite.gamecore

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DAILY_PAY_PERIOD_MILLIS: Long = 43_200_000L
private val DAILY_INCOME_REFRESH_INTERVAL = 60.seconds
private val DAILY_INCOME_LIFETIME = 365.days

@Serializable
data class ChangeDailyPayPayload(
    val ip: String,
    val port: Int,
    val change: String? = null,
    @SerialName("finalizeIP")
    val finalizeIp: String? = null,
    val attackPort: Int? = null,
)

class ChangeDailyPayCommand(
    private val actorStateId: GameStateId,
    private val targetStateId: GameStateId,
    private val targetPort: Int,
    private val requestedRevenueTargetStateId: GameStateId,
) : RequestCommand<ChangeDailyPayResponse> {
    override val name: String = "changedailypay"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(actorStateId, targetStateId)

    override suspend fun execute(context: CommandContext): ChangeDailyPayResponse {
        val actorState = context.requireExistingState(actorStateId)
        val targetState = context.requireExistingState(targetStateId)
        val targetPortState = targetState.requireRemotePortAccess(
            portNumber = targetPort,
            now = System.currentTimeMillis(),
            allowFrozen = false,
            commandName = name,
        )
        return performChangeDailyPay(
            context = context,
            actorState = actorState,
            targetState = targetState,
            targetPortState = targetPortState,
            requestedRevenueTargetStateId = requestedRevenueTargetStateId,
        )
    }
}

internal class RefreshDailyIncomeRuntimeCommand(
    private val stateId: GameStateId,
    private val interestRegistry: InterestRegistry? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<Unit> {
    override val name: String = "refreshdailyincomeruntime"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = emptySet()

    override suspend fun execute(context: CommandContext) {
        if (interestRegistry != null && interestRegistry.subscribersFor(stateId).isEmpty()) {
            return
        }
        val sourceState = context.loadState(stateId) ?: return
        val revenueTargetStateId = sourceState.dailyPay.resolvedRevenueTargetStateId(sourceState.id)
        context.request(
            DailyIncomePayoutCommand(
                sourceStateId = stateId,
                revenueTargetStateId = revenueTargetStateId,
                clock = clock,
            ),
        )
    }
}

internal class DailyIncomePayoutCommand(
    private val sourceStateId: GameStateId,
    private val revenueTargetStateId: GameStateId,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RequestCommand<Unit> {
    override val name: String = "dailyincomepayout"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(sourceStateId, revenueTargetStateId)

    override suspend fun execute(context: CommandContext) {
        val sourceState = context.loadState(sourceStateId) ?: return
        val now = clock()
        val currentDailyPay = sourceState.dailyPay
        if (currentDailyPay.lastPaidAtEpochMillis <= 100L) {
            updateDailyPayState(
                context = context,
                stateId = sourceStateId,
                previous = currentDailyPay,
                next = currentDailyPay.copy(lastPaidAtEpochMillis = now),
            )
            return
        }
        if (currentDailyPay.inactive) {
            appendLegacyHostLog(
                context = context,
                stateId = sourceStateId,
                sourceIp = sourceStateId.value,
                createdAtEpochMillis = now,
                message = "Did not receive income because you were inactive.",
            )
            updateDailyPayState(
                context = context,
                stateId = sourceStateId,
                previous = currentDailyPay,
                next = currentDailyPay.copy(
                    lastPaidAtEpochMillis = now,
                    inactive = false,
                ),
            )
            return
        }
        if (now - currentDailyPay.lastPaidAtEpochMillis <= DAILY_PAY_PERIOD_MILLIS) {
            return
        }

        val dueAt = currentDailyPay.lastPaidAtEpochMillis + DAILY_PAY_PERIOD_MILLIS
        val updatedVotes = minOf(4, sourceState.website.votesAvailable + 1)
        if (updatedVotes != sourceState.website.votesAvailable) {
            context.appendEvents(
                id = sourceStateId,
                events = listOf(
                    WebsiteVotesAvailableAdjustedEvent(delta = updatedVotes - sourceState.website.votesAvailable),
                ),
            )
        }

        val sourcePortState = sourceState.activeDefaultApplicationPort(ApplicationKind.HTTP, dueAt)
        if (sourcePortState == null) {
            appendLegacyHostLog(
                context = context,
                stateId = sourceStateId,
                sourceIp = sourceStateId.value,
                createdAtEpochMillis = dueAt,
                message = "Did not receive income from website because HTTP is not installed.",
            )
            updateDailyPayState(
                context = context,
                stateId = sourceStateId,
                previous = currentDailyPay,
                next = currentDailyPay.copy(lastPaidAtEpochMillis = now),
            )
            return
        }

        val redirectTargetId = currentDailyPay.resolvedRevenueTargetStateId(sourceStateId)
        val redirectTargetState = if (redirectTargetId == sourceStateId) {
            sourceState
        } else {
            context.loadState(redirectTargetId) ?: ComputerState.empty(
                id = redirectTargetId,
                playerIp = redirectTargetId.value,
            )
        }

        val httpLevel = legacyLevelForXp(sourceState.stats.skillExperience(ScriptFamily.HTTP))
        val levelBonus = if (sourceState.identity.isNpc) 0.0 else max(0, httpLevel - 1) * 50.0
        val totalPay = currentDailyPay.baseAmount + levelBonus
        val redirected = totalPay * 0.75 * currentDailyPay.reductionMultiplier
        val retainedPettyCash = totalPay * 0.75 - redirected
        val guaranteedBank = totalPay * 0.25
        val nextLastPaid = currentDailyPay.lastPaidAtEpochMillis + DAILY_PAY_PERIOD_MILLIS

        if (redirectTargetId == sourceStateId) {
            val pettyCashBefore = sourceState.economy.pettyCash
            val pettyCashDelta = retainedPettyCash + redirected
            val events = buildList {
                if (pettyCashDelta != 0.0 || guaranteedBank != 0.0) {
                    add(
                        EconomyBalanceAdjustedEvent(
                            pettyCashDelta = pettyCashDelta,
                            bankMoneyDelta = guaranteedBank,
                        ),
                    )
                }
                if (httpLevel > 0) {
                    add(HttpExperienceAdjustedEvent(delta = httpLevel * 10.0))
                }
                if (retainedPettyCash > 0.0) {
                    add(
                        HostLogAppendedEvent(
                            entry = legacyHostLogEntry(
                                createdAtEpochMillis = dueAt,
                                sourceIp = sourceStateId.value,
                                message = "Transferred ${currencyString(retainedPettyCash)} of daily pay from ${sourceStateId.value}.",
                            ),
                        ),
                    )
                }
                add(
                    HostLogAppendedEvent(
                        entry = legacyHostLogEntry(
                            createdAtEpochMillis = dueAt,
                            sourceIp = sourceStateId.value,
                            message = "Transferred ${currencyString(redirected)} of daily pay from ${sourceStateId.value}.",
                        ),
                    ),
                )
                add(
                    HostLogAppendedEvent(
                        entry = legacyHostLogEntry(
                            createdAtEpochMillis = dueAt,
                            sourceIp = sourceStateId.value,
                            message = "Received $$guaranteedBank in guaranteed income to bank.",
                        ),
                    ),
                )
                add(
                    DailyPayStateUpdatedEvent(
                        changedPathList = setOf("dailyPay.lastPaidAtEpochMillis"),
                        dailyPay = currentDailyPay.copy(lastPaidAtEpochMillis = nextLastPaid),
                    ),
                )
            }
            val updatedSource = context.appendEvents(sourceStateId, events)
            context.evaluatePassivePettyCashChange(
                targetStateId = sourceStateId,
                previousPettyCash = pettyCashBefore,
                newPettyCash = updatedSource.economy.pettyCash,
                sourceIp = sourceStateId.value,
                external = false,
            )
            return
        }

        val sourcePettyCashBefore = sourceState.economy.pettyCash
        val targetPettyCashBefore = redirectTargetState.economy.pettyCash
        val updatedSource = context.appendEvents(
            id = sourceStateId,
            events = listOf(
                EconomyBalanceAdjustedEvent(
                    pettyCashDelta = retainedPettyCash,
                    bankMoneyDelta = guaranteedBank,
                ),
                HostLogAppendedEvent(
                    entry = legacyHostLogEntry(
                        createdAtEpochMillis = dueAt,
                        sourceIp = sourceStateId.value,
                        message = if (retainedPettyCash > 0.0) {
                            "Transferred ${currencyString(retainedPettyCash)} of daily pay from ${sourceStateId.value}."
                        } else {
                            "Received $$guaranteedBank in guaranteed income to bank."
                        },
                    ),
                ),
                HostLogAppendedEvent(
                    entry = legacyHostLogEntry(
                        createdAtEpochMillis = dueAt,
                        sourceIp = sourceStateId.value,
                        message = "Received $$guaranteedBank in guaranteed income to bank.",
                    ),
                ),
                DailyPayStateUpdatedEvent(
                    changedPathList = setOf("dailyPay.lastPaidAtEpochMillis"),
                    dailyPay = currentDailyPay.copy(lastPaidAtEpochMillis = nextLastPaid),
                ),
            ).let { baseEvents ->
                if (retainedPettyCash > 0.0) baseEvents else baseEvents.filterIndexed { index, _ -> index != 1 }
            },
        )
        val updatedTarget = context.appendEvents(
            id = redirectTargetId,
            events = listOf(
                EconomyBalanceAdjustedEvent(pettyCashDelta = redirected),
                HttpExperienceAdjustedEvent(delta = httpLevel * 10.0),
                HostLogAppendedEvent(
                    entry = legacyHostLogEntry(
                        createdAtEpochMillis = dueAt,
                        sourceIp = sourceStateId.value,
                        message = "Transferred ${currencyString(redirected)} of daily pay from ${sourceStateId.value}.",
                    ),
                ),
            ),
        )

        context.evaluatePassivePettyCashChange(
            targetStateId = sourceStateId,
            previousPettyCash = sourcePettyCashBefore,
            newPettyCash = updatedSource.economy.pettyCash,
            sourceIp = sourceStateId.value,
            external = false,
        )
        context.evaluatePassivePettyCashChange(
            targetStateId = redirectTargetId,
            previousPettyCash = targetPettyCashBefore,
            newPettyCash = updatedTarget.economy.pettyCash,
            sourceIp = sourceStateId.value,
            external = true,
        )
    }
}

class DailyIncomeProgramCommand(
    private val stateId: GameStateId,
    override val programId: String,
    private val dailyIncomeProgramRegistry: DailyIncomeProgramRegistry = NoOpDailyIncomeProgramRegistry,
    private val interestRegistry: InterestRegistry? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ProgramCommand {
    override val name: String = "daily-income-program"
    override val lifetime: CommandLifetime = CommandLifetime(DAILY_INCOME_LIFETIME)
    override val targetStateIds: Set<GameStateId> = emptySet()
    override val programUpdateStateIds: Set<GameStateId> = emptySet()
    override val programType: String = "daily-income"
    override val tickInterval = DAILY_INCOME_REFRESH_INTERVAL

    override suspend fun onStart(context: CommandContext): ProgramExecutionStep {
        context.request(
            RefreshDailyIncomeRuntimeCommand(
                stateId = stateId,
                interestRegistry = interestRegistry,
                clock = clock,
            ),
        )
        return ProgramExecutionStep(
            status = ProgramLifecycleStatus.RUNNING,
            relatedStateIds = emptySet(),
        )
    }

    override suspend fun onTick(context: CommandContext): ProgramExecutionStep {
        if (!dailyIncomeProgramRegistry.hasProgram(stateId)) {
            return ProgramExecutionStep(
                status = ProgramLifecycleStatus.COMPLETED,
                relatedStateIds = emptySet(),
            )
        }
        context.request(
            RefreshDailyIncomeRuntimeCommand(
                stateId = stateId,
                interestRegistry = interestRegistry,
                clock = clock,
            ),
        )
        return ProgramExecutionStep(
            status = ProgramLifecycleStatus.RUNNING,
            relatedStateIds = emptySet(),
        )
    }

    override suspend fun onCancel(context: CommandContext, reason: String) {
        dailyIncomeProgramRegistry.unregister(programId)
    }
}

internal suspend fun performChangeDailyPay(
    context: CommandContext,
    actorState: ComputerState,
    targetState: ComputerState,
    targetPortState: PortState,
    requestedRevenueTargetStateId: GameStateId,
): ChangeDailyPayResponse {
    val currentRevenueTargetStateId = targetState.dailyPay.resolvedRevenueTargetStateId(targetState.id)
    if (!targetPortState.isHttpApplication()) {
        return publishChangeDailyPayUiEvents(
            context = context,
            response = ChangeDailyPayResponse(
                actorStateId = actorState.id,
                targetStateId = targetState.id,
                targetPort = targetPortState.number,
                requestedRevenueTargetStateId = requestedRevenueTargetStateId,
                accepted = true,
                outcome = ChangeDailyPayOutcome.WRONG_PORT_TYPE,
                message = "change-daily-pay-wrong-port-type",
                reductionMultiplierAfter = targetState.dailyPay.reductionMultiplier,
                revenueTargetStateIdAfter = currentRevenueTargetStateId,
                requesterHttpExperienceAfter = actorState.stats.skillExperience(ScriptFamily.HTTP),
                actorVersion = actorState.version,
                targetVersion = targetState.version,
            ),
        )
    }

    val actionProfile = targetPortState.installedFirewall?.actionProfile ?: FirewallActionProfile()
    if (actionProfile.changeDailyPayFailChance > 0.0) {
        return publishChangeDailyPayUiEvents(
            context = context,
            response = ChangeDailyPayResponse(
                actorStateId = actorState.id,
                targetStateId = targetState.id,
                targetPort = targetPortState.number,
                requestedRevenueTargetStateId = requestedRevenueTargetStateId,
                accepted = true,
                outcome = ChangeDailyPayOutcome.FIREWALL_NOOP,
                message = "change-daily-pay-firewall-noop",
                reductionMultiplierAfter = targetState.dailyPay.reductionMultiplier,
                revenueTargetStateIdAfter = currentRevenueTargetStateId,
                requesterHttpExperienceAfter = actorState.stats.skillExperience(ScriptFamily.HTTP),
                actorVersion = actorState.version,
                targetVersion = targetState.version,
            ),
        )
    }

    if (targetState.dailyPay.lastBountyHttpStateId == actorState.id) {
        return publishChangeDailyPayUiEvents(
            context = context,
            response = ChangeDailyPayResponse(
                actorStateId = actorState.id,
                targetStateId = targetState.id,
                targetPort = targetPortState.number,
                requestedRevenueTargetStateId = requestedRevenueTargetStateId,
                accepted = true,
                outcome = ChangeDailyPayOutcome.BOUNTY_GUARD,
                message = "change-daily-pay-bounty-guard",
                reductionMultiplierAfter = targetState.dailyPay.reductionMultiplier,
                revenueTargetStateIdAfter = currentRevenueTargetStateId,
                requesterHttpExperienceAfter = actorState.stats.skillExperience(ScriptFamily.HTTP),
                actorVersion = actorState.version,
                targetVersion = targetState.version,
            ),
        )
    }

    val reductionMultiplier = actionProfile.changeDailyPayReductionMultiplier
    if (requestedRevenueTargetStateId == currentRevenueTargetStateId) {
        val nextDailyPay = targetState.dailyPay.copy(reductionMultiplier = reductionMultiplier)
        val updatedTarget = if (nextDailyPay == targetState.dailyPay) {
            targetState
        } else {
            updateDailyPayState(
                context = context,
                stateId = targetState.id,
                previous = targetState.dailyPay,
                next = nextDailyPay,
            )
        }
        return publishChangeDailyPayUiEvents(
            context = context,
            response = ChangeDailyPayResponse(
                actorStateId = actorState.id,
                targetStateId = targetState.id,
                targetPort = targetPortState.number,
                requestedRevenueTargetStateId = requestedRevenueTargetStateId,
                accepted = true,
                outcome = ChangeDailyPayOutcome.ALREADY_CONTROLLED,
                message = "change-daily-pay-already-controlled",
                reductionMultiplierAfter = nextDailyPay.reductionMultiplier,
                revenueTargetStateIdAfter = nextDailyPay.resolvedRevenueTargetStateId(targetState.id),
                requesterHttpExperienceAfter = actorState.stats.skillExperience(ScriptFamily.HTTP),
                actorVersion = actorState.version,
                targetVersion = updatedTarget.version,
            ),
        )
    }

    val bountyMatch = actorState.findMatchingChangeBounty(targetState.id)
    val updatedActor = context.appendEvents(
        id = actorState.id,
        events = buildList {
            add(
                HttpExperienceAdjustedEvent(
                    delta = if (targetState.identity.isNpc) {
                        10.0
                    } else {
                        10.0 + legacyLevelForXp(targetState.stats.skillExperience(ScriptFamily.HTTP)) * 10.0
                    },
                ),
            )
            if (bountyMatch != null) {
                add(FileDeletedEvent(bountyMatch.file.path))
                bountyMatch.remaining?.let { remaining ->
                    add(FileSavedEvent(remaining))
                }
            }
        },
    )
    val nextDailyPay = targetState.dailyPay.copy(
        reductionMultiplier = reductionMultiplier,
        revenueTargetStateId = requestedRevenueTargetStateId,
        lastBountyHttpStateId = bountyMatch?.let { actorState.id } ?: targetState.dailyPay.lastBountyHttpStateId,
    )
    val updatedTarget = updateDailyPayState(
        context = context,
        stateId = targetState.id,
        previous = targetState.dailyPay,
        next = nextDailyPay,
    )
    return publishChangeDailyPayUiEvents(
        context = context,
        response = ChangeDailyPayResponse(
            actorStateId = actorState.id,
            targetStateId = targetState.id,
            targetPort = targetPortState.number,
            requestedRevenueTargetStateId = requestedRevenueTargetStateId,
            accepted = true,
            outcome = ChangeDailyPayOutcome.SUCCESS,
            message = "change-daily-pay-success",
            reductionMultiplierAfter = nextDailyPay.reductionMultiplier,
            revenueTargetStateIdAfter = nextDailyPay.resolvedRevenueTargetStateId(targetState.id),
            requesterHttpExperienceAfter = updatedActor.stats.skillExperience(ScriptFamily.HTTP),
            actorVersion = updatedActor.version,
            targetVersion = updatedTarget.version,
        ),
    )
}

private suspend fun publishChangeDailyPayUiEvents(
    context: CommandContext,
    response: ChangeDailyPayResponse,
): ChangeDailyPayResponse {
    val events = when (response.outcome) {
        ChangeDailyPayOutcome.SUCCESS -> listOf(
            AttackMessageUiEvent(
                message = "Daily pay successfully changed.",
                port = response.targetPort,
                ip = response.targetStateId.value,
            ),
            TextMessageUiEvent("Daily pay successfully changed."),
        )

        ChangeDailyPayOutcome.ALREADY_CONTROLLED -> listOf(
            AttackMessageUiEvent(
                message = "You already controlled this HTTP.",
                port = response.targetPort,
                ip = response.targetStateId.value,
            ),
        )

        ChangeDailyPayOutcome.WRONG_PORT_TYPE -> listOf(
            PopupUiEvent(
                message = "You can only change daily pay on an HTTP port.",
                style = PopupUiStyle.ERROR,
            ),
        )

        ChangeDailyPayOutcome.BOUNTY_GUARD -> listOf(
            PopupUiEvent(
                message = "You cannot immediately take back over an HTTP attacked as part of a bounty.",
                style = PopupUiStyle.ERROR,
            ),
        )

        ChangeDailyPayOutcome.FIREWALL_NOOP -> listOf(
            PopupUiEvent(
                message = "${response.targetStateId.value}'s firewall has caused the change daily pay to fail.",
                style = PopupUiStyle.MESSAGE,
            ),
        )
    }
    events.forEach { event ->
        context.publishUiEvent(
            targetStateIds = setOf(response.actorStateId),
            event = event,
        )
    }
    return response
}

private suspend fun updateDailyPayState(
    context: CommandContext,
    stateId: GameStateId,
    previous: DailyPayState,
    next: DailyPayState,
): ComputerState {
    if (previous == next) {
        return context.loadState(stateId) ?: ComputerState.empty(stateId, playerIp = stateId.value)
    }
    return context.appendEvents(
        id = stateId,
        events = listOf(
            DailyPayStateUpdatedEvent(
                changedPathList = buildSet {
                    if (previous.baseAmount != next.baseAmount) {
                        add("dailyPay.baseAmount")
                    }
                    if (previous.reductionMultiplier != next.reductionMultiplier) {
                        add("dailyPay.reductionMultiplier")
                    }
                    if (previous.revenueTargetStateId != next.revenueTargetStateId) {
                        add("dailyPay.revenueTargetStateId")
                    }
                    if (previous.lastPaidAtEpochMillis != next.lastPaidAtEpochMillis) {
                        add("dailyPay.lastPaidAtEpochMillis")
                    }
                    if (previous.inactive != next.inactive) {
                        add("dailyPay.inactive")
                    }
                    if (previous.lastBountyHttpStateId != next.lastBountyHttpStateId) {
                        add("dailyPay.lastBountyHttpStateId")
                    }
                }.ifEmpty { setOf("dailyPay") },
                dailyPay = next,
            ),
        ),
    )
}

private suspend fun appendLegacyHostLog(
    context: CommandContext,
    stateId: GameStateId,
    sourceIp: String,
    createdAtEpochMillis: Long,
    message: String,
) {
    context.appendEvents(
        id = stateId,
        events = listOf(
            HostLogAppendedEvent(
                entry = legacyHostLogEntry(
                    createdAtEpochMillis = createdAtEpochMillis,
                    sourceIp = sourceIp,
                    message = message,
                ),
            ),
        ),
    )
}

private fun legacyHostLogEntry(
    createdAtEpochMillis: Long,
    sourceIp: String,
    message: String,
): ComputerLogEntry {
    return ComputerLogEntry(
        createdAtEpochMillis = createdAtEpochMillis,
        renderedLine = renderLegacyLogLine(createdAtEpochMillis, message),
        sourceIp = sourceIp,
    )
}

private fun currencyString(amount: Double): String {
    return NumberFormat.getCurrencyInstance(Locale.US).format(amount)
}

private fun DailyPayState.resolvedRevenueTargetStateId(fallback: GameStateId): GameStateId {
    return revenueTargetStateId ?: fallback
}

private data class MatchingChangeBounty(
    val file: StoredFile,
    val remaining: StoredFile?,
)

private fun ComputerState.findMatchingChangeBounty(targetStateId: GameStateId): MatchingChangeBounty? {
    val file = filesystem.filesByPath.values
        .sortedBy { it.path.lowercase(Locale.US) }
        .firstOrNull { storedFile ->
            val metadata = storedFile.bountyMetadata
            storedFile.kind == StoredFileKind.BOUNTY &&
                metadata != null &&
                metadata.type == BountyTypes.CHANGE &&
                (metadata.target == targetStateId.value || metadata.target == "*")
        }
        ?: return null
    return MatchingChangeBounty(
        file = file,
        remaining = if (file.quantity <= 1) null else file.copy(quantity = file.quantity - 1),
    )
}

private fun PortState.isHttpApplication(): Boolean {
    return installedApplication?.kind == ApplicationKind.HTTP || type.equals("http", ignoreCase = true)
}

private fun ComputerState.activeDefaultApplicationPort(
    kind: ApplicationKind,
    now: Long,
): PortState? {
    return ports.firstOrNull { port ->
        port.defaultPort &&
            port.enabled &&
            !port.dummy &&
            !port.isFrozenAt(now) &&
            port.installedApplication?.kind == kind
    }
}
