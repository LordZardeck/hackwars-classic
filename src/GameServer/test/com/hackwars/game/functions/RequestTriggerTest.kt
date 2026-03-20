package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Test
import org.mockito.kotlin.verify

class RequestTriggerTest {
    @Test
    fun execute_callsWatchHandlerByIndex() {
        val computer = FunctionTestSupport.baseComputer()
        val watchHandler = computer.watchHandler
        val triggerParam = hashMapOf<Any, Any>("key" to "value")

        RequestTrigger(computer).execute(FunctionTestSupport.requestTrigger(3, triggerParam, "target-ip"))

        verify(watchHandler).triggerWatch(3, "target-ip", triggerParam)
    }
}
