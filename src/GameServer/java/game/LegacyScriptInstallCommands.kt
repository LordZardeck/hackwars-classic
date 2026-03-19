package game;

import java.util.HashMap;

/**
 * Legacy extraction for malicious script installation requests that still live
 * in {@link Computer#processQueuedItem(Object, long)}.
 */
public class LegacyScriptInstallCommands implements LegacyApplicationDataHandler {
    @Override
    public boolean dispatch(Computer computer, ApplicationData applicationData, int resolvedPort) {
        if (!"requestinstallscript".equals(applicationData.getFunction())) {
            return false;
        }

        String targetIP = (String) ((Object[]) applicationData.getParameters())[0];
        int targetPort = (Integer) ((Object[]) applicationData.getParameters())[1];
        String path = (String) ((Object[]) applicationData.getParameters())[2];
        String file = (String) ((Object[]) applicationData.getParameters())[3];
        Object maliciousParameters = (Object[]) ((Object[]) applicationData.getParameters())[4];

        HackerFile hackerFile = computer.MyFileSystem.getFile(path, file);
        if (hackerFile != null) {
            hackerFile.setQuantity(hackerFile.getQuantity() - 1);
            if (hackerFile.getQuantity() <= 0) {
                computer.MyFileSystem.deleteFile(path, file);
            }

            HashMap content = hackerFile.getContent();
            Object[] payload = new Object[]{content, maliciousParameters};
            computer.MyComputerHandler.addData(new ApplicationData("installScript", payload, targetPort, computer.ip), targetIP);
        }

        computer.systemChange = true;
        return true;
    }
}
