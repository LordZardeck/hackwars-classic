package game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Legacy bridge for transaction/event logging.
 *
 * Callers still use the historical singleton API, but output now flows through
 * the shared SLF4J logging system instead of writing ad hoc files relative to
 * the current working directory.
 */
public final class CentralLogging {
    private static final CentralLogging Instance = new CentralLogging();
    private static final Logger Logger = LoggerFactory.getLogger(CentralLogging.class);

    private CentralLogging() {
    }

    /**
     * Get an instance of this logger bridge.
     */
    public static CentralLogging getInstance() {
        return Instance;
    }

    /**
     * Add a string to be output through the shared logging pipeline.
     */
    public void addOutput(String data) {
        String message = normalize(data);
        if (message == null || message.isEmpty()) {
            return;
        }
        Logger.info(message);
    }

    private static String normalize(String data) {
        if (data == null) {
            return null;
        }

        int endIndex = data.length();
        while (endIndex > 0) {
            char current = data.charAt(endIndex - 1);
            if (current != '\n' && current != '\r') {
                break;
            }
            endIndex--;
        }
        return data.substring(0, endIndex);
    }
}
