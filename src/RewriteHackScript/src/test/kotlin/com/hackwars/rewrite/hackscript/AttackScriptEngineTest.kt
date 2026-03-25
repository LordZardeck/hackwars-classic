package com.hackwars.rewrite.hackscript

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttackScriptEngineTest {
    private val engine = AttackScriptEngine()

    @Test
    fun executesInitializeContinueAndFinalizeSlotsWithLockedHelperSemantics() {
        val initialize = engine.execute(
            script = """
                int main() {
                    logMessage(getSourceIP());
                    logMessage("" + getSourcePort());
                    logMessage(getTargetIP());
                    logMessage("" + getTargetPort());
                    logMessage("" + getTargetHP());
                    logMessage("" + getTargetCPUCost());
                    logMessage("" + checkForWatch());
                    logMessage("" + checkPettyCash());
                    logMessage("" + getHP());
                    logMessage("" + getCPULoad());
                    logMessage("" + getMaximumCPULoad());
                    logMessage("" + getIterations());
                    return 0;
                }
            """.trimIndent(),
            input = input(
                phase = AttackExecutionPhase.INITIALIZE,
                targetHealth = 100.0,
                iterations = 0,
            ),
        )
        val continuePhase = engine.execute(
            script = """int main() { logMessage("" + getTargetHP()); logMessage("" + getIterations()); return 0; }""",
            input = input(
                phase = AttackExecutionPhase.CONTINUE,
                targetHealth = 88.5,
                iterations = 3,
            ),
        )
        val finalize = engine.execute(
            script = """int main() { logMessage("" + getTargetHP()); logMessage("" + getIterations()); return 0; }""",
            input = input(
                phase = AttackExecutionPhase.FINALIZE,
                targetHealth = 0.0,
                iterations = 4,
            ),
        )

        assertTrue(initialize.diagnostics.isEmpty())
        assertEquals(
            listOf(
                "ATTACKER-IP",
                "12",
                "TARGET-IP",
                "25",
                "100",
                "7.5",
                "true",
                "30",
                "91",
                "14",
                "100",
                "0",
            ),
            assertNotNull(initialize.result).effects
                .filterIsInstance<AttackAppendHostLogEffect>()
                .map { it.message },
        )
        assertEquals(
            listOf("88.5", "3"),
            assertNotNull(continuePhase.result).effects
                .filterIsInstance<AttackAppendHostLogEffect>()
                .map { it.message },
        )
        assertEquals(
            listOf("0", "4"),
            assertNotNull(finalize.result).effects
                .filterIsInstance<AttackAppendHostLogEffect>()
                .map { it.message },
        )
    }

    @Test
    fun logMessageProducesTypedRuntimeEffect() {
        val outcome = engine.execute(
            script = """int main() { logMessage("attack"); return 0; }""",
            input = input(phase = AttackExecutionPhase.CONTINUE),
        )

        val result = assertNotNull(outcome.result)
        assertEquals("attack", assertIs<AttackAppendHostLogEffect>(result.effects.single()).message)
    }

    @Test
    fun continueAndFinalizeHelpersProduceTypedEffectsInSupportedPhases() {
        val continueOutcome = engine.execute(
            script = """
                int main() {
                    editLogs("old", "new");
                    deleteLogs("REMOTE-IP");
                    destroyWatches();
                    emptyPettyCash();
                    stealFile();
                    installScript();
                    switchAttack();
                    cancelAttack();
                    freeze();
                    return 0;
                }
            """.trimIndent(),
            input = input(phase = AttackExecutionPhase.CONTINUE),
        )
        val finalizeOutcome = engine.execute(
            script = """
                int main() {
                    editLogs("finish", "done");
                    deleteLogs("OTHER-IP");
                    destroyWatches();
                    emptyPettyCash();
                    stealFile();
                    installScript();
                    return 0;
                }
            """.trimIndent(),
            input = input(phase = AttackExecutionPhase.FINALIZE),
        )

        assertTrue(continueOutcome.diagnostics.isEmpty())
        assertEquals(
            listOf(
                AttackEditTargetLogsEffect("old", "new"),
                AttackDeleteTargetLogsEffect("REMOTE-IP"),
                AttackDestroyTargetWatchesEffect,
                AttackEmptyTargetPettyCashEffect,
                AttackStealTargetFileEffect,
                AttackInstallTargetScriptEffect,
                AttackSwitchTargetEffect,
                AttackCancelCurrentAttackEffect,
                AttackFreezeTargetPortEffect,
            ),
            assertNotNull(continueOutcome.result).effects,
        )
        assertTrue(finalizeOutcome.diagnostics.isEmpty())
        assertEquals(
            listOf(
                AttackEditTargetLogsEffect("finish", "done"),
                AttackDeleteTargetLogsEffect("OTHER-IP"),
                AttackDestroyTargetWatchesEffect,
                AttackEmptyTargetPettyCashEffect,
                AttackStealTargetFileEffect,
                AttackInstallTargetScriptEffect,
            ),
            assertNotNull(finalizeOutcome.result).effects,
        )
    }

    @Test
    fun unsupportedPhaseUsageAddsDiagnosticsButDoesNotCrash() {
        val initializeDelete = engine.execute(
            script = """int main() { deleteLogs("REMOTE-IP"); return 0; }""",
            input = input(phase = AttackExecutionPhase.INITIALIZE),
        )
        val finalizeCancel = engine.execute(
            script = """int main() { cancelAttack(); return 0; }""",
            input = input(phase = AttackExecutionPhase.FINALIZE),
        )
        val initializeFreeze = engine.execute(
            script = """int main() { freeze(); return 0; }""",
            input = input(phase = AttackExecutionPhase.INITIALIZE),
        )
        val finalizeSwitch = engine.execute(
            script = """int main() { switchAttack(); return 0; }""",
            input = input(phase = AttackExecutionPhase.FINALIZE),
        )
        val initializeBerserk = engine.execute(
            script = """int main() { berserk(); return 0; }""",
            input = input(phase = AttackExecutionPhase.INITIALIZE),
        )
        val initializeDestroyWatches = engine.execute(
            script = """int main() { destroyWatches(); return 0; }""",
            input = input(phase = AttackExecutionPhase.INITIALIZE),
        )
        val initializeEmptyPettyCash = engine.execute(
            script = """int main() { emptyPettyCash(); return 0; }""",
            input = input(phase = AttackExecutionPhase.INITIALIZE),
        )
        val initializeStealFile = engine.execute(
            script = """int main() { stealFile(); return 0; }""",
            input = input(phase = AttackExecutionPhase.INITIALIZE),
        )
        val initializeInstallScript = engine.execute(
            script = """int main() { installScript(); return 0; }""",
            input = input(phase = AttackExecutionPhase.INITIALIZE),
        )

        assertEquals("UNSUPPORTED_PHASE", initializeDelete.diagnostics.single().code)
        assertEquals(emptyList(), assertNotNull(initializeDelete.result).effects)
        assertEquals("UNSUPPORTED_PHASE", finalizeCancel.diagnostics.single().code)
        assertEquals(emptyList(), assertNotNull(finalizeCancel.result).effects)
        assertEquals("UNSUPPORTED_PHASE", initializeFreeze.diagnostics.single().code)
        assertEquals(emptyList(), assertNotNull(initializeFreeze.result).effects)
        assertEquals("UNSUPPORTED_PHASE", finalizeSwitch.diagnostics.single().code)
        assertEquals(emptyList(), assertNotNull(finalizeSwitch.result).effects)
        assertEquals("UNSUPPORTED_PHASE", initializeBerserk.diagnostics.single().code)
        assertEquals(emptyList(), assertNotNull(initializeBerserk.result).effects)
        assertEquals("UNSUPPORTED_PHASE", initializeDestroyWatches.diagnostics.single().code)
        assertEquals(emptyList(), assertNotNull(initializeDestroyWatches.result).effects)
        assertEquals("UNSUPPORTED_PHASE", initializeEmptyPettyCash.diagnostics.single().code)
        assertEquals(emptyList(), assertNotNull(initializeEmptyPettyCash.result).effects)
        assertEquals("UNSUPPORTED_PHASE", initializeStealFile.diagnostics.single().code)
        assertEquals(emptyList(), assertNotNull(initializeStealFile.result).effects)
        assertEquals("UNSUPPORTED_PHASE", initializeInstallScript.diagnostics.single().code)
        assertEquals(emptyList(), assertNotNull(initializeInstallScript.result).effects)
    }

    @Test
    fun berserkIsOnlyEmittedOncePerExecution() {
        val outcome = engine.execute(
            script = """int main() { berserk(); berserk(); return 0; }""",
            input = input(phase = AttackExecutionPhase.CONTINUE),
        )

        assertEquals(listOf(AttackBerserkEffect), assertNotNull(outcome.result).effects)
        assertEquals("HELPER_LIMIT_REACHED", outcome.diagnostics.single().code)
    }

    @Test
    fun unsupportedHelpersProduceStructuredFailureWithoutCrashing() {
        val outcome = engine.execute(
            script = """int main() { totallyUnsupported(); return 0; }""",
            input = input(phase = AttackExecutionPhase.CONTINUE),
        )

        assertNull(outcome.result)
        assertEquals("UNSUPPORTED_FUNCTION", outcome.diagnostics.single().code)
    }

    @Test
    fun parseFailuresReturnDiagnosticsWithoutThrowing() {
        val outcome = engine.execute(
            script = "int main( { return 0; }",
            input = input(phase = AttackExecutionPhase.CONTINUE),
        )

        assertNull(outcome.result)
        assertEquals("PARSE_ERROR", outcome.diagnostics.single().code)
    }

    private fun input(
        phase: AttackExecutionPhase,
        targetHealth: Double = 100.0,
        iterations: Int = 0,
    ): AttackExecutionInput {
        return AttackExecutionInput(
            phase = phase,
            sourceIp = "ATTACKER-IP",
            sourcePort = 12,
            targetIp = "TARGET-IP",
            targetPort = 25,
            targetHealth = targetHealth,
            targetCpuCost = 7.5,
            targetWatchPresent = true,
            targetPettyCash = 30.0,
            hostHealth = 91.0,
            currentCpuLoad = 14.0,
            maximumCpuLoad = 100.0,
            iterations = iterations,
        )
    }
}
