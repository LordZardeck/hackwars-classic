package game

import com.hackwars.game.functions.FunctionTestSupport
import com.hackwars.game.program.Program
import hackscript.model.TypeArray
import hackscript.model.TypeFloat
import hackscript.model.TypeInteger
import hackscript.model.TypeString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.whenever

class HackerLinkerTest {
    @Test
    fun runFunction_handles_string_array_helpers() {
        val linker = HackerLinker(TestProgram(), null)

        val replaced = linker.runFunction(
            "replaceAll",
            arrayListOf(TypeString("a.b.c"), TypeString("."), TypeString("x"))
        ) as TypeString
        val split = linker.runFunction(
            "split",
            arrayListOf(TypeString("alpha,beta,gamma"), TypeString(","))
        ) as TypeArray
        val length = linker.runFunction("length", arrayListOf(split)) as TypeInteger

        assertEquals("axbxc", replaced.stringValue)
        val values = split.rawValue as ArrayList<*>
        assertEquals(listOf("alpha", "beta", "gamma"), values.map { (it as TypeString).stringValue })
        assertEquals(3, length.intValue)
    }

    @Test
    fun runFunction_parse_helpers_follow_fileIo_gate() {
        val enabledLinker = HackerLinker(TestProgram(fileIo = true), null)
        val disabledLinker = HackerLinker(TestProgram(fileIo = false), null)

        val parsedFloat = enabledLinker.runFunction("parseFloat", arrayListOf(TypeString("12.5"))) as TypeFloat
        val parsedInt = enabledLinker.runFunction("parseInt", arrayListOf(TypeString("42"))) as TypeInteger

        assertEquals(12.5f, parsedFloat.floatValue, 0.0001f)
        assertEquals(42, parsedInt.intValue)
        assertNull(disabledLinker.runFunction("parseFloat", arrayListOf(TypeString("12.5"))))
        assertNull(disabledLinker.runFunction("parseInt", arrayListOf(TypeString("42"))))
    }

    @Test
    fun helper_methods_preserve_current_escape_and_counter_behavior() {
        val linker = HackerLinker(TestProgram(), null)

        val escaped = HackerLinker.regexEscape(".+$?[]()|{}\\")

        assertTrue("literal regex should match only the original string", Regex(escaped).matches(".+$?[]()|{}\\"))
        assertEquals(1, linker.incrementValue("message"))
        assertEquals(2, linker.incrementValue("message"))
        assertEquals(1, linker.incrementValue("other"))
    }

    private class TestProgram(fileIo: Boolean = true) : Program(baseComputer(fileIo), null) {
        override fun installScript(script: HashMap<*, *>) {
        }

        override fun execute(applicationData: ApplicationData) {
        }

        override fun getTypeKeys(): Array<String?> {
            return emptyArray()
        }

        override fun getContent(): HashMap<*, *> {
            return HashMap<Any?, Any?>()
        }

        override fun outputXML(): String {
            return ""
        }

        companion object {
            private fun baseComputer(fileIo: Boolean): Computer {
                val computer = FunctionTestSupport.baseComputer()
                whenever(computer.getFileIO()).thenReturn(fileIo)
                return computer
            }
        }
    }
}
