package com.hackwars.game.functions

import com.hackwars.rpc.SaveFile
import game.ApplicationData
import game.HackerFile
import hackscript.model.TypeBoolean
import hackscript.model.TypeFloat
import hackscript.model.TypeInteger
import hackscript.model.TypeString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

class RequestSaveTest {
    @Test
    fun execute_serializesVariablesIntoHackerFile_andDispatchesSavefile() {
        val computer = FunctionTestSupport.baseComputer("5.5.5.5")
        val networkSwitch = computer.computerHandler
        val trigger = hashMapOf<String, Any>(
            "s" to TypeString("hello"),
            "i" to TypeInteger(7),
            "f" to TypeFloat(3.5f),
            "b" to TypeBoolean(true)
        )

        RequestSave(computer).execute(FunctionTestSupport.requestSave("slotA", HashMap(trigger)))

        val appCaptor = argumentCaptor<ApplicationData>()
        verify(networkSwitch).addData(appCaptor.capture(), eq("5.5.5.5"))
        val dispatched = appCaptor.firstValue
        assertEquals("savefile", dispatched.command.wireName())
        val payload = dispatched.payload as SaveFile
        val savedFile = payload.name as HackerFile
        assertEquals("slotA.save", savedFile.name)
        assertEquals("slotA", savedFile.maker)
        val content = savedFile.content as HashMap<*, *>
        val data = content["data"] as String
        assertTrue(data.contains("s\tstring\thello"))
        assertTrue(data.contains("i\tint\t7"))
        assertTrue(data.contains("f\tfloat\t3.5"))
        assertTrue(data.contains("b\tbool\ttrue"))
    }
}
