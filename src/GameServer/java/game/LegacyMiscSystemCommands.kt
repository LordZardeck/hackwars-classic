package game

/**
 * Small legacy run-loop commands that mutate Computer state directly and do not
 * belong in port fallback dispatch.
 */
class LegacyMiscSystemCommands : LegacyApplicationDataHandler {
    override fun dispatch(computer: Computer, applicationData: ApplicationData, resolvedPort: Int): Boolean {
        val function = applicationData.function

        if (function == "cluedata") {
            return true
        } else if (function == "bountyhttp") {
            computer.lastBountyHTTP = applicationData.parameters as String
            return true
        } else if (function == "unlock") {
            val code = applicationData.parameters as String
            if (code == computer.unlockKey) {
                computer.lockCount = 0
                computer.locked = false
            } else {
                computer.RESEND_CAPTCHA = true
            }
            return true
        } else if (function == "ping") {
            return true
        } else if (function == "hacktendoTarget") {
            return true
        } else if (function == "hacktendoActivate") {
            println("Attempting to activate an object.")
            return true
        }

        return false
    }
}
