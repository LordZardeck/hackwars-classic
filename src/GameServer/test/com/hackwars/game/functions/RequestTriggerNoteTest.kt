package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Test
import org.mockito.kotlin.verify

class RequestTriggerNoteTest {
    @Test
    fun execute_callsWatchHandlerByNote() {
        val computer = FunctionTestSupport.baseComputer()
        val watchHandler = computer.watchHandler
        val triggerParam = hashMapOf<Any, Any>("key" to "value")

        RequestTriggerNote(computer).execute(
            ApplicationData(
                "requesttriggernote",
                arrayOf<Any>("alert-note", triggerParam, "target-ip"),
                0,
                "source"
            )
        )

        verify(watchHandler).triggerWatch("alert-note", "target-ip", triggerParam)
    }
}
