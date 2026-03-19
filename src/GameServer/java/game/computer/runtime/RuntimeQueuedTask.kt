package game.computer.runtime

data class RuntimeQueuedTask(
    val function: String,
    val sourceIp: String,
    val parameters: Any? = null,
    val port: Int = 0,
    val sourcePort: Int = 0,
    val source: Int = 0,
)
