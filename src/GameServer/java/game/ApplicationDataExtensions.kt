package game

import game.payload.MessageTextPayload
import game.payload.StructuredMessagePayload

inline fun <reified T : ApplicationPayload> ApplicationData.payloadAs(): T {
    return requirePayload(T::class.java)
}

@Suppress("UNCHECKED_CAST")
fun messageData(
    message: Any,
    sourceIp: String?,
    parameters: Array<Any?>? = null,
    portInfo: Array<Any?>? = null
): ApplicationData {
    val payload = when (message) {
        is String -> {
            if (parameters == null && portInfo == null) {
                MessageTextPayload(message)
            } else {
                StructuredMessagePayload(arrayOf(message), parameters, portInfo)
            }
        }

        is Array<*> -> StructuredMessagePayload(message as Array<Any?>, parameters, portInfo)
        else -> throw IllegalArgumentException("Unsupported message payload type: ${message::class.java.name}")
    }

    return ApplicationData(payload, 0, sourceIp ?: "")
}
