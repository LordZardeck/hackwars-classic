package game.computer.runtime

data class RuntimeQueuedTask(
    val function: String,
    val sourceIp: String,
    val parameters: Any? = null
)
