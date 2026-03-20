package game.computer.runtime

import game.ApplicationCommand
import game.ApplicationPayload

data class RuntimeQueuedTask(
    val command: ApplicationCommand,
    val payload: ApplicationPayload,
    val sourceIp: String,
    val port: Int = 0,
    val sourcePort: Int = 0,
    val source: Int = 0,
) {
    constructor(
        payload: ApplicationPayload,
        sourceIp: String,
        port: Int = 0,
        sourcePort: Int = 0,
        source: Int = 0,
    ) : this(payload.getCommand(), payload, sourceIp, port, sourcePort, source)
}
