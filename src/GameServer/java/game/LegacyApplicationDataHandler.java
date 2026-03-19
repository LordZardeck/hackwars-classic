package game;

/**
 * Shared contract for relocating legacy ApplicationData command branches out of
 * Computer.processQueuedItem without changing externally visible behavior.
 */
public interface LegacyApplicationDataHandler {
    boolean dispatch(Computer computer, ApplicationData applicationData, int resolvedPort);
}
