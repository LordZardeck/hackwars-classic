package hackerlogin

import com.hackwars.game.program.ToyProgram
import hackscript.model.TypeArray
import hackscript.model.TypeFloat
import hackscript.model.TypeInteger
import hackscript.model.TypeString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayList

class ToyLinkerTest {
    private fun toyProgram(): ToyProgram = ToyProgram("", 64, 64)

    private fun linker(program: ToyProgram = toyProgram()): ToyLinker = ToyLinker(program, null)

    @Test
    fun runFunction_replaceAllEscapesRegexCharacters() {
        val result = linker().runFunction(
            "replaceAll",
            arrayListOf(TypeString("a.b.c"), TypeString("."), TypeString("+"))
        ) as TypeString

        assertEquals("a+b+c", result.stringValue)
    }

    @Test
    fun runFunction_splitAndLengthUseCurrentArraySemantics() {
        val split = linker().runFunction(
            "split",
            arrayListOf(TypeString("alpha beta gamma"), TypeString(" "))
        ) as TypeArray

        @Suppress("UNCHECKED_CAST")
        val splitValues = split.rawValue as ArrayList<TypeString>
        assertEquals(listOf("alpha", "beta", "gamma"), splitValues.map { it.stringValue })

        val length = linker().runFunction("length", arrayListOf(split)) as TypeInteger
        assertEquals(3, length.intValue)
    }

    @Test
    fun runFunction_parseFloatAndParseIntProduceTypedValues() {
        val linker = linker()

        val parsedFloat = linker.runFunction("parseFloat", arrayListOf(TypeString("12.5"))) as TypeFloat
        val parsedInt = linker.runFunction("parseInt", arrayListOf(TypeString("42"))) as TypeInteger

        assertEquals(12.5f, parsedFloat.floatValue, 0.0f)
        assertEquals(42, parsedInt.intValue)
    }

    @Test
    fun runFunction_inputAndOutputHelpersRoundTripThroughToyProgramState() {
        val program = toyProgram().apply {
            addInString("message")
            addInFloat(4.5f)
            addInInt(9)
        }
        val linker = linker(program)

        val inputString = linker.runFunction("getInputString", arrayListOf<Any>()) as TypeString
        val inputFloat = linker.runFunction("getInputFloat", arrayListOf<Any>()) as TypeFloat
        val inputInt = linker.runFunction("getInputInt", arrayListOf<Any>()) as TypeInteger

        linker.runFunction("setOutputString", arrayListOf(inputString))
        linker.runFunction("setOutputFloat", arrayListOf(inputFloat))
        linker.runFunction("setOutputInt", arrayListOf(inputInt))

        assertEquals("message", inputString.stringValue)
        assertEquals(4.5f, inputFloat.floatValue, 0.0f)
        assertEquals(9, inputInt.intValue)
        assertTrue(program.getError().isNullOrEmpty())
        assertEquals(listOf("message"), program.getOutString().toList())
        assertEquals(listOf(4.5f), program.outDouble.toList())
        assertEquals(listOf(9), program.getOutInt().toList())
    }

}
