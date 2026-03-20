package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.whenever

class RequestTaskTest {
    @Test
    fun execute_whenQuestNotComplete_createsTaskEntry() {
        val computer = FunctionTestSupport.baseComputer()
        val quests = hashMapOf<Any?, Any?>(7 to arrayOf<Any?>(null, "QuestLabel"))
        whenever(computer.currentQuests).thenReturn(quests)
        whenever(computer.checkQuest(7)).thenReturn(false)

        RequestTask(computer).execute(FunctionTestSupport.requestTask("file", 7, "taskA"))

        val updated = quests[7] as Array<*>
        val taskMap = updated[0] as HashMap<*, *>
        assertTrue(taskMap.containsKey("taskA"))
    }
}
