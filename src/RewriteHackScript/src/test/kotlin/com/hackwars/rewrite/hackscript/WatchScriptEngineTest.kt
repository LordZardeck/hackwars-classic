package com.hackwars.rewrite.hackscript

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchScriptEngineTest {
    private val engine = WatchScriptEngine()

    @Test
    fun executesExplicitTriggerHelpersAndEmitsTypedRuntimeEffects() {
        val outcome = engine.execute(
            script = """
                int main() {
                    if (isTriggered()) {
                        logMessage("triggered");
                    }
                    if (isTriggerParameterSet("mode")) {
                        logMessage(getTriggerParameter("mode"));
                    }
                    if (!isTriggerParameterSet("missing")) {
                        logMessage("missing");
                    }
                    logMessage(getSourceIP());
                    logMessage(getTargetIP());
                    logMessage("" + getPort());
                    logMessage("" + getTargetPort());
                    logMessage("" + getDefaultBank());
                    logMessage("" + checkPettyCash());
                    logMessage("" + getCPULoad());
                    logMessage("" + getMaximumCPULoad());
                    depositPettyCash();
                    depositPettyCash(25);
                    return 0;
                }
            """.trimIndent(),
            input = WatchExecutionInput(
                hostIp = "HOST-IP",
                targetIp = "SOURCE-IP",
                sourceIp = "HOST-IP",
                targetPort = 0,
                installPort = 6,
                defaultBankPort = 21,
                pettyCash = 42.5,
                currentCpuLoad = 15.0,
                maximumCpuLoad = 100.0,
                triggered = true,
                external = true,
                triggerParameters = mapOf("mode" to StringHookValue("alpha")),
            ),
        )

        assertTrue(outcome.diagnostics.isEmpty())
        val result = assertNotNull(outcome.result)
        assertEquals(
            listOf(
                "triggered",
                "alpha",
                "missing",
                "HOST-IP",
                "SOURCE-IP",
                "6",
                "0",
                "21",
                "42.5",
                "15",
                "100",
            ),
            result.effects.filterIsInstance<AppendHostLogEffect>().map { it.message },
        )
        assertEquals(null, assertIs<DepositPettyCashEffect>(result.effects[11]).amount)
        assertEquals(25.0, assertIs<DepositPettyCashEffect>(result.effects[12]).amount)
    }

    @Test
    fun unsupportedHelpersProduceStructuredFailureWithoutCrashing() {
        val outcome = engine.execute(
            script = """
                int main() {
                    attack();
                    return 0;
                }
            """.trimIndent(),
            input = defaultInput(),
        )

        assertNull(outcome.result)
        assertEquals("UNSUPPORTED_FUNCTION", outcome.diagnostics.single().code)
    }

    @Test
    fun parseFailuresReturnDiagnosticsWithoutThrowing() {
        val outcome = engine.execute(
            script = "int main( { return 0; }",
            input = defaultInput(),
        )

        assertNull(outcome.result)
        assertEquals("PARSE_ERROR", outcome.diagnostics.single().code)
    }

    private fun defaultInput(): WatchExecutionInput {
        return WatchExecutionInput(
            hostIp = "HOST-IP",
            targetIp = "SOURCE-IP",
            sourceIp = "HOST-IP",
            installPort = 6,
            defaultBankPort = 21,
            pettyCash = 10.0,
            currentCpuLoad = 5.0,
            maximumCpuLoad = 100.0,
            triggered = true,
            external = true,
        )
    }
}
