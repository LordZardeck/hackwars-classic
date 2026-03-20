package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.payload.RequestTaskPayload
import game.payloadAs

/**
 * Represents a function that marks a quest task as completed.
 *
 * For a quest that is still active, this function:
 *
 * - Reads quest id and task name from application parameters.
 * - Loads the current quest task map.
 * - Creates the task map when missing.
 * - Stores the task completion marker for the provided task name.
 *
 * @constructor Initializes `RequestTask` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class RequestTask(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        val payload = applicationData.payloadAs<RequestTaskPayload>()
        val questId = payload.questId
        val taskName = payload.taskName

        if (computer.checkQuest(questId)) return

        val questData = computer.currentQuests[questId] as? Array<*> ?: return
        @Suppress("UNCHECKED_CAST")
        val currentQuest = questData.getOrNull(0) as? HashMap<Any, Any>
        val label = questData.getOrNull(1) as? String ?: ""
        val taskData = arrayOf<Any>(true, "")

        if (currentQuest == null) {
            val newQuest = HashMap<Any, Any>()
            newQuest[taskName] = taskData
            computer.currentQuests[questId] = arrayOf(newQuest, label)
        } else {
            currentQuest[taskName] = taskData
        }
    }
}
