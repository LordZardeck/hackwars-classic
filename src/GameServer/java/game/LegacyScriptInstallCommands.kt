package game

import java.util.HashMap

/**
 * Legacy extraction for malicious script installation requests that still live
 * in [Computer.processQueuedItem].
 */
class LegacyScriptInstallCommands : LegacyApplicationDataHandler {
    override fun dispatch(computer: Computer, applicationData: ApplicationData, resolvedPort: Int): Boolean {
        if ("requestinstallscript" != applicationData.function) {
            return false
        }

        val parameters = applicationData.parameters as Array<Any?>
        val targetIP = parameters[0] as String
        val targetPort = (parameters[1] as Number).toInt()
        val path = parameters[2] as String
        val file = parameters[3] as String
        val maliciousParameters = parameters[4]

        val hackerFile = computer.MyFileSystem.getFile(path, file)
        if (hackerFile != null) {
            hackerFile.setQuantity(hackerFile.getQuantity() - 1)
            if (hackerFile.getQuantity() <= 0) {
                computer.MyFileSystem.deleteFile(path, file)
            }

            val content = hackerFile.getContent() as HashMap<*, *>
            val payload = arrayOf<Any?>(content, maliciousParameters)
            computer.MyComputerHandler.addData(ApplicationData("installScript", payload, targetPort, computer.ip), targetIP)
        }

        computer.systemChange = true
        return true
    }
}
