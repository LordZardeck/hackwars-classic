package game

import com.hackwars.rpc.ClueData
import com.hackwars.rpc.HacktendoActivate
import com.hackwars.rpc.HacktendoTarget
import com.hackwars.rpc.Unlock
import game.payload.BountyHttpPayload
import game.payload.PingPayload

/**
 * Small legacy run-loop commands that mutate Computer state directly and do not
 * belong in port fallback dispatch.
 */
class LegacyMiscSystemCommands : LegacyApplicationDataHandler {
    override fun dispatch(computer: Computer, applicationData: ApplicationData, resolvedPort: Int): Boolean {
        val function = applicationData.command.wireName()

        if (function == com.hackwars.rpc.GameCommandWires.CLUEDATA) {
            applicationData.payloadAs<ClueData>()
            return true
        } else if (function == com.hackwars.rpc.GameCommandWires.BOUNTYHTTP) {
            val payload = applicationData.payloadAs<BountyHttpPayload>()
            computer.lastBountyHTTP = payload.bountyIp
            return true
        } else if (function == com.hackwars.rpc.GameCommandWires.UNLOCK) {
            val payload = applicationData.payloadAs<Unlock>()
            val code = payload.code
            if (code == computer.unlockKey) {
                computer.lockCount = 0
                computer.locked = false
            } else {
                computer.RESEND_CAPTCHA = true
            }
            return true
        } else if (function == com.hackwars.rpc.GameCommandWires.PING) {
            applicationData.payloadAs<PingPayload>()
            return true
        } else if (function == com.hackwars.rpc.GameCommandWires.HACKTENDO_TARGET) {
            applicationData.payloadAs<HacktendoTarget>()
            return true
        } else if (function == com.hackwars.rpc.GameCommandWires.HACKTENDO_ACTIVATE) {
            applicationData.payloadAs<HacktendoActivate>()
            println("Attempting to activate an object.")
            return true
        }

        return false
    }
}
