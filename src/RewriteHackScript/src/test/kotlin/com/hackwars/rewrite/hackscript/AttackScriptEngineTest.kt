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
                targetHealth = 100.0,
                iterations = 0,
            ),
        )
        val continuePhase = engine.execute(
            script = """int main() { logMessage("" + getTargetHP()); logMessage("" + getIterations()); return 0; }""",
            input = input(
                targetHealth = 88.5,
                iterations = 3,
            ),
        )
        val finalize = engine.execute(
            script = """int main() { logMessage("" + getTargetHP()); logMessage("" + getIterations()); return 0; }""",
            input = input(
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
            input = input(),
        )

        val result = assertNotNull(outcome.result)
        assertEquals("attack", assertIs<AttackAppendHostLogEffect>(result.effects.single()).message)
    }

    @Test
    fun unsupportedHelpersProduceStructuredFailureWithoutCrashing() {
        val outcome = engine.execute(
            script = """int main() { freeze(); return 0; }""",
            input = input(),
        )

        assertNull(outcome.result)
        assertEquals("UNSUPPORTED_FUNCTION", outcome.diagnostics.single().code)
    }

    @Test
    fun parseFailuresReturnDiagnosticsWithoutThrowing() {
        val outcome = engine.execute(
            script = "int main( { return 0; }",
            input = input(),
        )

        assertNull(outcome.result)
        assertEquals("PARSE_ERROR", outcome.diagnostics.single().code)
    }

    private fun input(
        targetHealth: Double = 100.0,
        iterations: Int = 0,
    ): AttackExecutionInput {
        return AttackExecutionInput(
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
