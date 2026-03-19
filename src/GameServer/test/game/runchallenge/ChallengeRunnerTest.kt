package game.runchallenge

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeRunnerTest {
    @Suppress("UNCHECKED_CAST")
    private fun arrayResult(result: HashMap<*, *>, key: String): Array<Any?> = result[key] as Array<Any?>

    @Test
    fun runToyProblem_attemptsSupportedFunctionButReturnsCurrentNullApplicationDataError() {
        val result = ChallengeRunner.getInstance().runToyProblem(
            "setOutputString(\"done\");",
            8,
            emptyArray<Any>(),
            emptyArray<Any>(),
            emptyArray<Any>(),
            emptyArray<Any>(),
            arrayOf<Any>("done"),
            emptyArray<Any>()
        )

        assertEquals(
            "Syntax error in challenge code compiler returned [Parameter specified as non-null is null: method com.hackwars.game.program.ToyProgram.execute, parameter applicationData]",
            result["error"]
        )
        assertEquals(false, result["success"])
        assertArrayEquals(emptyArray<Any?>(), arrayResult(result, "outstring"))
        assertArrayEquals(emptyArray<Any?>(), arrayResult(result, "outdouble"))
        assertArrayEquals(emptyArray<Any?>(), arrayResult(result, "outint"))
    }

    @Test
    fun runToyProblem_rejectsUnsupportedFunctionsBeforeExecution() {
        val result = ChallengeRunner.getInstance().runToyProblem(
            "totallyUnknown();",
            8,
            emptyArray<Any>(),
            emptyArray<Any>(),
            emptyArray<Any>(),
            emptyArray<Any>(),
            emptyArray<Any>(),
            emptyArray<Any>()
        )

        assertFalse(result["success"] as Boolean)
        assertEquals("Function totallyUnknown not found.", result["error"])
        assertArrayEquals(emptyArray<Any?>(), arrayResult(result, "outstring"))
        assertArrayEquals(emptyArray<Any?>(), arrayResult(result, "outdouble"))
        assertArrayEquals(emptyArray<Any?>(), arrayResult(result, "outint"))
    }
}
