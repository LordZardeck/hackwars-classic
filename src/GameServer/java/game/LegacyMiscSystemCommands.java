package game;

/**
 * Small legacy run-loop commands that mutate Computer state directly and do not
 * belong in port fallback dispatch.
 */
public class LegacyMiscSystemCommands implements LegacyApplicationDataHandler {
    @Override
    public boolean dispatch(Computer computer, ApplicationData applicationData, int resolvedPort) {
        String function = applicationData.getFunction();

        if (function.equals("cluedata")) {
            return true;
        } else if (function.equals("bountyhttp")) {
            computer.lastBountyHTTP = (String) applicationData.getParameters();
            return true;
        } else if (function.equals("unlock")) {
            String code = (String) applicationData.getParameters();
            if (code.equals(computer.unlockKey)) {
                computer.lockCount = 0;
                computer.locked = false;
            } else {
                computer.RESEND_CAPTCHA = true;
            }
            return true;
        } else if (function.equals("ping")) {
            return true;
        } else if (function.equals("hacktendoTarget")) {
            return true;
        } else if (function.equals("hacktendoActivate")) {
            System.out.println("Attempting to activate an object.");
            return true;
        }

        return false;
    }
}
