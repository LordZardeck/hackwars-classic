package game

import game.payload.InstallScriptPayload
import game.payload.RequestInstallScriptPayload
import game.payloadAs
import java.util.HashMap

/**
 * Legacy extraction for malicious script installation requests that still live
 * in [Computer.processQueuedItem].
 */
class LegacyScriptInstallCommands : LegacyApplicationDataHandler {
    override fun dispatch(computer: Computer, applicationData: ApplicationData, resolvedPort: Int): Boolean {
        if ("requestinstallscript" != applicationData.command.wireName()) {
            return false
        }

        val payload = applicationData.payloadAs<RequestInstallScriptPayload>()

        val hackerFile = computer.MyFileSystem.getFile(payload.path, payload.file)
        if (hackerFile != null) {
            hackerFile.setQuantity(hackerFile.getQuantity() - 1)
            if (hackerFile.getQuantity() <= 0) {
                computer.MyFileSystem.deleteFile(payload.path, payload.file)
            }

            val content = hackerFile.getContent() as HashMap<*, *>
            val forwardedPayload = InstallScriptPayload(content, payload.maliciousParameters)
            computer.MyComputerHandler.addData(
                ApplicationData(forwardedPayload, payload.targetPort, computer.ip),
                payload.targetIp
            )
        }

        computer.systemChange = true
        return true
    }
}
