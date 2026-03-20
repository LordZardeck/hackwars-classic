package game

import com.hackwars.rpc.ClueData
import com.hackwars.rpc.HacktendoActivate
import com.hackwars.rpc.HacktendoTarget
import com.hackwars.rpc.Unlock
import game.payload.BountyHttpPayload
import game.payload.PingPayload
import game.payloadAs

/**
 * Small legacy run-loop commands that mutate Computer state directly and do not
 * belong in port fallback dispatch.
 */
class LegacyMiscSystemCommands : LegacyApplicationDataHandler {
    override fun dispatch(computer: Computer, applicationData: ApplicationData, resolvedPort: Int): Boolean {
        val function = applicationData.command.wireName()

        if (function == "cluedata") {
            applicationData.payloadAs<ClueData>()
            return true
        } else if (function == "bountyhttp") {
            val payload = applicationData.payloadAs<BountyHttpPayload>()
            computer.lastBountyHTTP = payload.bountyIp
            return true
        } else if (function == "unlock") {
            val payload = applicationData.payloadAs<Unlock>()
            val code = payload.code
            if (code == computer.unlockKey) {
                computer.lockCount = 0
                computer.locked = false
            } else {
                computer.RESEND_CAPTCHA = true
            }
            return true
        } else if (function == "ping") {
            applicationData.payloadAs<PingPayload>()
            return true
        } else if (function == "hacktendoTarget") {
            applicationData.payloadAs<HacktendoTarget>()
            return true
        } else if (function == "hacktendoActivate") {
            applicationData.payloadAs<HacktendoActivate>()
            println("Attempting to activate an object.")
            return true
        }

        return false
    }
}
