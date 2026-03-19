package game.computer.runtime

class AbuseProtectionTickService {
    fun tick(state: RuntimeTickState): List<RuntimeTickEvent> {
        val events = mutableListOf<RuntimeTickEvent>()
        state.grantFilesCounter += 1
        if (state.grantFilesCounter % state.grantFilesInterval == 0) {
            events += RuntimeTickEvent.GrantFilesRequested
        }

        if (!state.npc && state.loaded && !state.loading && state.lockCount >= state.captchaThreshold && (!state.locked || state.resendCaptcha)) {
            state.locked = true
            val payload = state.captchaGenerator?.invoke()
                ?: RuntimeCaptchaPayload(state.unlockKey.ifBlank { "00000" }, IntArray(0))
            state.unlockKey = payload.unlockKey
            events += RuntimeTickEvent.CaptchaRequested(payload)
            state.resendCaptcha = false
        }

        return events
    }
}
