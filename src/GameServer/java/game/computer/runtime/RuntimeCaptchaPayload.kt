package game.computer.runtime

data class RuntimeCaptchaPayload(
    val unlockKey: String,
    val image: IntArray
)
