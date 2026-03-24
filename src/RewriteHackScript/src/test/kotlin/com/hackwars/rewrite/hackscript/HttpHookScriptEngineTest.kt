package com.hackwars.rewrite.hackscript

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
                    popUp("boom");
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
}
