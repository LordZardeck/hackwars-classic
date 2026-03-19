package com.hackwars.game.functions

import game.ApplicationData
import game.Port
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SavePortNoteTest {
    @Test
    fun execute_updatesNoteForMatchingPort() {
        val computer = FunctionTestSupport.baseComputer()
        val port = mock<Port>()
        whenever(port.number).thenReturn(99)
        whenever(computer.ports).thenReturn(hashMapOf(99 to port))

        SavePortNote(computer).execute(ApplicationData("saveportnote", "important", 99, "source"))

        verify(port).setNote("important")
    }
}
