package com.hackwars.rewrite.hackscript

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpHookScriptEngineTest {
    private val engine = HttpHookScriptEngine()

    @Test
    fun executesEnterScriptHelpersAndRenderMutations() {
        val outcome = engine.execute(
            script = """
                int main() {
                    string visitor = getVisitorIP();
                    string host = getHostIP();
                    replaceContent("visitor", visitor);
                    replaceContent("host", host);
                    if (isGetVariableSet("q")) {
                        replaceContent("query", fetchGetVariable("q"));
                    }
                    hideStore();
                    return 0;
                }
            """.trimIndent(),
            input = HttpHookExecutionInput(
                visitorIp = "visitor.example",
                hostIp = "10.0.0.1",
                initialBody = "<html><?visitor?>-<?host?>-<?query?></html>",
                queryParameters = mapOf("q" to "search"),
            ),
        )

        assertTrue(outcome.diagnostics.isEmpty())
        val result = assertNotNull(outcome.result)
        assertEquals("<html>visitor.example-10.0.0.1-search</html>", result.body)
        assertFalse(result.includeStore)
    }

    @Test
    fun executesTypedExpressionsLoopsAndCommonBuiltins() {
        val outcome = engine.execute(
            script = """
                int main() {
                    int counter = 0;
                    string joined = "";
                    while (counter < 3) {
                        joined = joined + parseInt("2");
                        counter = counter + 1;
                    }
                    string value = replaceAll("a.b.c", ".", "x");
                    string finalValue = value + "-" + length(split("alpha,beta", ",")) + "-" + joined;
                    replaceContent("value", finalValue);
                    return 1;
                }
            """.trimIndent(),
            input = HttpHookExecutionInput(
                visitorIp = "visitor",
                hostIp = "host",
                initialBody = "<body><?value?></body>",
            ),
        )

        assertTrue(outcome.diagnostics.isEmpty())
        assertEquals("<body>axbxc-2-222</body>", outcome.result?.body)
        assertTrue(outcome.result?.includeStore == true)
    }

    @Test
    fun parameterHelpersUseSubmittedFormValues() {
        val outcome = engine.execute(
            script = """
                int main() {
                    if (isParameterSet("name")) {
                        replaceContent("name", getParameter("name"));
                    }
                    return 0;
                }
            """.trimIndent(),
            input = HttpHookExecutionInput(
                visitorIp = "visitor",
                hostIp = "host",
                initialBody = "<body><?name?></body>",
                formParameters = mapOf("name" to "Alice"),
            ),
        )

        assertTrue(outcome.diagnostics.isEmpty())
        assertEquals("<body>Alice</body>", outcome.result?.body)
    }

    @Test
    fun unsupportedFunctionProducesStructuredFailureWithoutResult() {
        val outcome = engine.execute(
            script = """
                int main() {
                    playSound("boom");
                    return 0;
                }
            """.trimIndent(),
            input = HttpHookExecutionInput(
                visitorIp = "visitor",
                hostIp = "host",
                initialBody = "<body><?name?></body>",
            ),
        )

        assertNull(outcome.result)
        assertEquals("UNSUPPORTED_FUNCTION", outcome.diagnostics.single().code)
    }

    @Test
    fun sideEffectHelpersReturnTypedEffectsAndLimitPopupsPerSlot() {
        val outcome = engine.execute(
            script = """
                int main() {
                    logMessage("visited");
                    popUp("one");
                    popUp("two");
                    popUp("three");
                    popUp("four");
                    popUp("five");
                    triggerWatch(3, "mode", "alpha", "parts", split("a,b", ","));
                    return 0;
                }
            """.trimIndent(),
            input = HttpHookExecutionInput(
                visitorIp = "visitor",
                hostIp = "host",
                initialBody = "<body>ok</body>",
            ),
        )

        val result = assertNotNull(outcome.result)
        assertEquals(6, result.effects.size)
        assertIs<AppendHostLog>(result.effects[0])
        assertTrue(result.effects.filterIsInstance<PopupToVisitor>().map { it.message } == listOf("one", "two", "three", "four"))
        val watch = assertIs<TriggerLocalWatch>(result.effects.last())
        assertEquals(3, watch.index)
        assertEquals(StringHookValue("alpha"), watch.parameters["mode"])
        assertEquals(ArrayHookValue(listOf(StringHookValue("a"), StringHookValue("b"))), watch.parameters["parts"])
        assertEquals("POPUP_LIMIT_EXCEEDED", outcome.diagnostics.single().code)
    }

    @Test
    fun triggerWatchRemoteRequiresNpcHostAndPreservesParametersWhenAllowed() {
        val denied = engine.execute(
            script = """
                int main() {
                    triggerWatchRemote(2, "TARGET-IP", "threshold", 5, "armed", true);
                    return 0;
                }
            """.trimIndent(),
            input = HttpHookExecutionInput(
                visitorIp = "visitor",
                hostIp = "host",
                hostIsNpc = false,
                initialBody = "<body>ok</body>",
            ),
        )
        assertTrue(denied.result?.effects?.isEmpty() == true)
        assertEquals("REMOTE_WATCH_REQUIRES_NPC_HOST", denied.diagnostics.single().code)

        val allowed = engine.execute(
            script = """
                int main() {
                    triggerWatchRemote(2, "TARGET-IP", "threshold", 5, "armed", true);
                    return 0;
                }
            """.trimIndent(),
            input = HttpHookExecutionInput(
                visitorIp = "visitor",
                hostIp = "host",
                hostIsNpc = true,
                initialBody = "<body>ok</body>",
            ),
        )
        val effect = assertIs<TriggerRemoteWatch>(assertNotNull(allowed.result).effects.single())
        assertEquals("TARGET-IP", effect.targetIp)
        assertEquals(IntHookValue(5), effect.parameters["threshold"])
        assertEquals(BooleanHookValue(true), effect.parameters["armed"])
    }
}
