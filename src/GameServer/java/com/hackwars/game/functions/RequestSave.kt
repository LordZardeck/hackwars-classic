package com.hackwars.game.functions

import game.ApplicationData
import game.Computer
import game.HackerFile
import hackscript.model.TypeBoolean
import hackscript.model.TypeFloat
import hackscript.model.TypeInteger
import hackscript.model.TypeString
import hackscript.model.Variable

/**
 * Represents a function that serializes game state variables into a `.save` file.
 *
 * This function creates a text `HackerFile` containing typed key/value rows and:
 *
 * - Reads file name and optional trigger variable map from application data.
 * - Serializes supported variable types (`string`, `int`, `float`, `bool`).
 * - Stores serialized data in the file content attributes.
 * - Dispatches a `savefile` request to persist the generated file.
 *
 * @constructor Initializes `RequestSave` for the provided computer.
 *
 * @param computer The `Computer` instance on which this function operates.
 */
class RequestSave(computer: Computer) : Function(computer) {
    override fun execute(applicationData: ApplicationData) {
        val parameters = applicationData.parameters as? Array<*> ?: return
        val fileName = parameters.getOrNull(0) as? String ?: return
        val triggerParameters = parameters.getOrNull(1) as? HashMap<*, *>

        val newFile = HackerFile(HackerFile.TEXT)
        newFile.description = "A save file for $fileName."
        newFile.maker = fileName
        newFile.quantity = 0

        val serializedData = buildString {
            triggerParameters?.forEach { (key, value) ->
                val variable = value as? Variable ?: return@forEach
                val variableKey = key as? String ?: return@forEach
                val variableType = when (variable) {
                    is TypeString -> "string"
                    is TypeInteger -> "int"
                    is TypeFloat -> "float"
                    is TypeBoolean -> "bool"
                    else -> null
                } ?: return@forEach

                append(variableKey)
                append('\t')
                append(variableType)
                append('\t')
                append(variable)
                append('\n')
            }
        }

        val attributes = HashMap<String, String>()
        attributes["data"] = serializedData
        attributes["level"] = ""

        newFile.content = attributes
        newFile.name = "$fileName.save"

        val payload = arrayOf("", newFile)
        computer.computerHandler.addData(ApplicationData("savefile", payload, 0, computer.getIP()), computer.getIP())
    }
}
