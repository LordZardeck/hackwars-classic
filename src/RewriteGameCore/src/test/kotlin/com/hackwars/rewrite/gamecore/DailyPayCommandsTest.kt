package com.hackwars.rewrite.gamecore

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TEST_DAILY_PAY_PERIOD_MILLIS: Long = 43_200_000L

class DailyPayCommandsTest {
    @Test
    fun changeDailyPaySuccessConsumesMatchingBountyRefreshesReductionAndAwardsHttpXp() = runTest {
        val actorId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val revenueTargetId = GameStateId("REV-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                actorId to actorState(
                    actorId,
                    filesystemFiles = listOf(
                        StoredFile(
                            path = buildFilePath("/Bounties", "change.bnt"),
                            name = "change.bnt",
                            kind = StoredFileKind.BOUNTY,
                            quantity = 1,
                            bountyMetadata = BountyMetadata(
                                type = BountyTypes.CHANGE,
                                target = targetId.value,
                                iterationsRemaining = 1,
                                reward = 25.0,
                                bountySourceStateId = GameStateId("STORE-IP"),
                            ),
                        ),
                    ),
                ),
                targetId to httpTargetState(
                    targetId,
                    actionProfile = FirewallActionProfile(
                        changeDailyPayReductionMultiplier = 0.4,
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("actor-conn", actorId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = ChangeDailyPayCommand(
                actorStateId = actorId,
                targetStateId = targetId,
                targetPort = 80,
                requestedRevenueTargetStateId = revenueTargetId,
            ),
            metadata = CommandMetadata(connectionId = "actor-conn", requestId = "changedailypay-1"),
            publisher = publisher,
        )

        val updatedActor = requireNotNull(repository.load(actorId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertTrue(response.accepted)
        assertEquals(ChangeDailyPayOutcome.SUCCESS, response.outcome)
        assertEquals(0.4, response.reductionMultiplierAfter)
        assertEquals(revenueTargetId, response.revenueTargetStateIdAfter)
        assertEquals(20.0, response.requesterHttpExperienceAfter)
        assertEquals(20.0, updatedActor.stats.skillExperience(ScriptFamily.HTTP))
        assertNull(updatedActor.filesystem.resolveFile("/Bounties", "change.bnt"))
        assertEquals(revenueTargetId, updatedTarget.dailyPay.revenueTargetStateId)
        assertEquals(0.4, updatedTarget.dailyPay.reductionMultiplier)
        assertEquals(actorId, updatedTarget.dailyPay.lastBountyHttpStateId)
        assertEquals(listOf(setOf("stats", "filesystem"), setOf("dailyPay")), publisher.deltas.map { it.second.deltaKeys })
        assertEquals(listOf(setOf("actor-conn"), setOf("actor-conn")), publisher.uiEvents.map { it.first })
        val successAttackMessage = assertIs<AttackMessageUiEvent>(publisher.uiEvents[0].second)
        assertEquals("Daily pay successfully changed.", successAttackMessage.message)
        assertEquals(80, successAttackMessage.port)
        assertEquals(targetId.value, successAttackMessage.ip)
        assertEquals(
            "Daily pay successfully changed.",
            assertIs<TextMessageUiEvent>(publisher.uiEvents[1].second).message,
        )
    }

    @Test
    fun changeDailyPayAlreadyControlledRefreshesReductionWithoutXpOrBountyConsumption() = runTest {
        val actorId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val revenueTargetId = GameStateId("REV-IP")
        val bountyFile = StoredFile(
            path = buildFilePath("/Bounties", "change.bnt"),
            name = "change.bnt",
            kind = StoredFileKind.BOUNTY,
            quantity = 2,
            bountyMetadata = BountyMetadata(
                type = BountyTypes.CHANGE,
                target = targetId.value,
                iterationsRemaining = 1,
                reward = 25.0,
                bountySourceStateId = GameStateId("STORE-IP"),
            ),
        )
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                actorId to actorState(actorId, filesystemFiles = listOf(bountyFile)),
                targetId to httpTargetState(
                    targetId,
                    actionProfile = FirewallActionProfile(
                        changeDailyPayReductionMultiplier = 0.25,
                    ),
                    dailyPay = DailyPayState(
                        revenueTargetStateId = revenueTargetId,
                        reductionMultiplier = 1.0,
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("actor-conn", actorId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = ChangeDailyPayCommand(
                actorStateId = actorId,
                targetStateId = targetId,
                targetPort = 80,
                requestedRevenueTargetStateId = revenueTargetId,
            ),
            metadata = CommandMetadata(connectionId = "actor-conn", requestId = "changedailypay-2"),
            publisher = publisher,
        )

        val updatedActor = requireNotNull(repository.load(actorId))
        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(ChangeDailyPayOutcome.ALREADY_CONTROLLED, response.outcome)
        assertEquals(0.0, updatedActor.stats.skillExperience(ScriptFamily.HTTP))
        assertEquals(2, requireNotNull(updatedActor.filesystem.resolveFile("/Bounties", "change.bnt")).quantity)
        assertEquals(0.25, updatedTarget.dailyPay.reductionMultiplier)
        assertEquals(revenueTargetId, updatedTarget.dailyPay.revenueTargetStateId)
        assertEquals(listOf(setOf("dailyPay")), publisher.deltas.map { it.second.deltaKeys })
        assertEquals(1, publisher.uiEvents.size)
        assertEquals(setOf("actor-conn"), publisher.uiEvents.single().first)
        val alreadyControlledMessage = assertIs<AttackMessageUiEvent>(publisher.uiEvents.single().second)
        assertEquals("You already controlled this HTTP.", alreadyControlledMessage.message)
        assertEquals(80, alreadyControlledMessage.port)
        assertEquals(targetId.value, alreadyControlledMessage.ip)
    }

    @Test
    fun changeDailyPayWrongPortTypeAndFirewallNoopStayAcceptedNoOps() = runTest {
        val actorId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val wrongPortPublisher = RecordingGameStatePublisher()
        val wrongPortInterests = InMemoryInterestRegistry().apply {
            register("actor-conn", actorId)
        }
        val dispatcher = DefaultCommandDispatcher(
            repository = InMemoryComputerStateRepository(
                seededStates = mapOf(
                    actorId to actorState(actorId),
                    targetId to httpTargetState(
                        targetId,
                        installedApplication = InstalledApplication(
                            name = "bank.bin",
                            kind = ApplicationKind.BANKING,
                            banking = true,
                        ),
                        type = "bank",
                    ),
                ),
            ),
            interestRegistry = wrongPortInterests,
        )

        val wrongPort = dispatcher.request(
            command = ChangeDailyPayCommand(
                actorStateId = actorId,
                targetStateId = targetId,
                targetPort = 80,
                requestedRevenueTargetStateId = GameStateId("REV-IP"),
            ),
            metadata = CommandMetadata(connectionId = "actor-conn", requestId = "wrong-port"),
            publisher = wrongPortPublisher,
        )

        val firewallRepository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                actorId to actorState(actorId),
                targetId to httpTargetState(
                    targetId,
                    actionProfile = FirewallActionProfile(
                        changeDailyPayFailChance = 1.0,
                        changeDailyPayReductionMultiplier = 0.25,
                    ),
                ),
            ),
        )
        val firewallPublisher = RecordingGameStatePublisher()
        val firewallInterests = InMemoryInterestRegistry().apply {
            register("actor-conn", actorId)
        }
        val firewallDispatcher = DefaultCommandDispatcher(firewallRepository, firewallInterests)
        val firewallNoop = firewallDispatcher.request(
            command = ChangeDailyPayCommand(
                actorStateId = actorId,
                targetStateId = targetId,
                targetPort = 80,
                requestedRevenueTargetStateId = GameStateId("REV-IP"),
            ),
            metadata = CommandMetadata(connectionId = "actor-conn", requestId = "firewall-noop"),
            publisher = firewallPublisher,
        )

        assertEquals(ChangeDailyPayOutcome.WRONG_PORT_TYPE, wrongPort.outcome)
        assertTrue(wrongPort.accepted)
        assertEquals(ChangeDailyPayOutcome.FIREWALL_NOOP, firewallNoop.outcome)
        assertTrue(firewallNoop.accepted)
        assertEquals(targetId, requireNotNull(firewallRepository.load(targetId)).dailyPay.revenueTargetStateId)
        val wrongPortPopup = assertIs<PopupUiEvent>(wrongPortPublisher.uiEvents.single().second)
        assertEquals(setOf("actor-conn"), wrongPortPublisher.uiEvents.single().first)
        assertEquals("You can only change daily pay on an HTTP port.", wrongPortPopup.message)
        assertEquals(PopupUiStyle.ERROR, wrongPortPopup.style)
        val firewallPopup = assertIs<PopupUiEvent>(firewallPublisher.uiEvents.single().second)
        assertEquals(setOf("actor-conn"), firewallPublisher.uiEvents.single().first)
        assertEquals("${targetId.value}'s firewall has caused the change daily pay to fail.", firewallPopup.message)
        assertEquals(PopupUiStyle.MESSAGE, firewallPopup.style)
    }

    @Test
    fun changeDailyPayBountyGuardPublishesPopupErrorWithoutMutatingState() = runTest {
        val actorId = GameStateId("LOCAL-IP")
        val targetId = GameStateId("TARGET-IP")
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                actorId to actorState(actorId),
                targetId to httpTargetState(
                    targetId,
                    dailyPay = DailyPayState(
                        revenueTargetStateId = targetId,
                        lastBountyHttpStateId = actorId,
                    ),
                ),
            ),
        )
        val interests = InMemoryInterestRegistry().apply {
            register("actor-conn", actorId)
            register("target-conn", targetId)
        }
        val publisher = RecordingGameStatePublisher()
        val dispatcher = DefaultCommandDispatcher(repository, interests)

        val response = dispatcher.request(
            command = ChangeDailyPayCommand(
                actorStateId = actorId,
                targetStateId = targetId,
                targetPort = 80,
                requestedRevenueTargetStateId = GameStateId("REV-IP"),
            ),
            metadata = CommandMetadata(connectionId = "actor-conn", requestId = "bounty-guard"),
            publisher = publisher,
        )

        val updatedTarget = requireNotNull(repository.load(targetId))

        assertEquals(ChangeDailyPayOutcome.BOUNTY_GUARD, response.outcome)
        assertEquals(targetId, updatedTarget.dailyPay.revenueTargetStateId)
        assertTrue(publisher.deltas.isEmpty())
        val popup = assertIs<PopupUiEvent>(publisher.uiEvents.single().second)
        assertEquals(setOf("actor-conn"), publisher.uiEvents.single().first)
        assertEquals(
            "You cannot immediately take back over an HTTP attacked as part of a bounty.",
            popup.message,
        )
        assertEquals(PopupUiStyle.ERROR, popup.style)
    }

    @Test
    fun refreshDailyIncomeRuntimeCreditsSourceAndRedirectTargetAndEmitsPassivePettyCashTriggers() = runTest {
        val sourceId = GameStateId("LOCAL-IP")
        val redirectId = GameStateId("REV-IP")
        val passiveWatchSink = RecordingPassiveWatchTriggerSink()
        val repository = InMemoryComputerStateRepository(
            seededStates = mapOf(
                sourceId to actorState(
                    sourceId,
                    pettyCash = 10.0,
                    bankMoney = 5.0,
                    httpExperience = 0.0,
                    votesAvailable = 3,
                    dailyPay = DailyPayState(
                        revenueTargetStateId = redirectId,
                        reductionMultiplier = 0.5,
                        lastPaidAtEpochMillis = 1_000L,
                    ),
                ),
                redirectId to ComputerState.empty(redirectId, playerIp = redirectId.value),
            ),
        )
        val dispatcher = DefaultCommandDispatcher(
            repository = repository,
            interestRegistry = InMemoryInterestRegistry(),
            passiveWatchTriggerSink = passiveWatchSink,
        )

        dispatcher.request(
            command = RefreshDailyIncomeRuntimeCommand(
                stateId = sourceId,
                clock = { 1_000L + TEST_DAILY_PAY_PERIOD_MILLIS + 1L },
            ),
        )

        val updatedSource = requireNotNull(repository.load(sourceId))
        val updatedRedirect = requireNotNull(repository.load(redirectId))

        assertEquals(385.0, updatedSource.economy.pettyCash)
        assertEquals(255.0, updatedSource.economy.bankMoney)
        assertEquals(4, updatedSource.website.votesAvailable)
        assertEquals(1_000L + TEST_DAILY_PAY_PERIOD_MILLIS, updatedSource.dailyPay.lastPaidAtEpochMillis)
        assertEquals(375.0, updatedRedirect.economy.pettyCash)
        assertEquals(10.0, updatedRedirect.stats.skillExperience(ScriptFamily.HTTP))
        assertEquals(2, updatedSource.logs.entries.size)
        assertEquals(1, updatedRedirect.logs.entries.size)
        assertEquals(
            listOf<PassiveWatchTrigger>(
                PassiveWatchTrigger.PettyCashChanged(
                    targetStateId = sourceId,
                    sourceIp = sourceId.value,
                    external = false,
                    previousPettyCash = 10.0,
                    newPettyCash = 385.0,
                ),
                PassiveWatchTrigger.PettyCashChanged(
                    targetStateId = redirectId,
                    sourceIp = sourceId.value,
                    external = true,
                    previousPettyCash = 0.0,
                    newPettyCash = 375.0,
                ),
            ),
            passiveWatchSink.triggers,
        )
    }

    private class RecordingPassiveWatchTriggerSink : PassiveWatchTriggerSink {
        val triggers = mutableListOf<PassiveWatchTrigger>()

        override suspend fun emit(context: CommandContext, trigger: PassiveWatchTrigger) {
            triggers += trigger
        }
    }

    private fun actorState(
        stateId: GameStateId,
        pettyCash: Double = 100.0,
        bankMoney: Double = 0.0,
        httpExperience: Double = 0.0,
        votesAvailable: Int = 0,
        dailyPay: DailyPayState = DailyPayState(revenueTargetStateId = stateId),
        filesystemFiles: List<StoredFile> = emptyList(),
    ): ComputerState {
        return ComputerState.empty(id = stateId, playFabId = "PF-${stateId.value}").copy(
            economy = EconomyState(
                pettyCash = pettyCash,
                bankMoney = bankMoney,
                defaultBankPort = 6,
            ),
            website = WebsiteState(votesAvailable = votesAvailable),
            dailyPay = dailyPay,
            stats = PlayerStatsState(
                experienceByFamily = mapOf(ScriptFamily.HTTP to httpExperience),
            ),
            filesystem = filesystemFiles.fold(FilesystemState()) { filesystem, file ->
                filesystem.saveFile(file)
            },
            ports = listOf(
                PortState(
                    number = 6,
                    type = "bank",
                    enabled = true,
                    defaultPort = false,
                    installedApplication = InstalledApplication(
                        name = "bank.bin",
                        kind = ApplicationKind.BANKING,
                        banking = true,
                    ),
                ),
                PortState(
                    number = 80,
                    type = "http",
                    enabled = true,
                    defaultPort = true,
                    installedApplication = InstalledApplication(
                        name = "site.bin",
                        kind = ApplicationKind.HTTP,
                    ),
                ),
            ),
        )
    }

    private fun httpTargetState(
        stateId: GameStateId,
        installedApplication: InstalledApplication = InstalledApplication(
            name = "site.bin",
            kind = ApplicationKind.HTTP,
        ),
        type: String = "http",
        actionProfile: FirewallActionProfile = FirewallActionProfile(),
        dailyPay: DailyPayState = DailyPayState(revenueTargetStateId = stateId),
    ): ComputerState {
        return ComputerState.empty(id = stateId, playFabId = "PF-${stateId.value}").copy(
            dailyPay = dailyPay,
            ports = listOf(
                PortState(
                    number = 80,
                    type = type,
                    enabled = true,
                    defaultPort = true,
                    installedApplication = installedApplication,
                    installedFirewall = InstalledFirewall(
                        name = "site-fw.bin",
                        actionProfile = actionProfile,
                    ),
                ),
            ),
        )
    }
}
