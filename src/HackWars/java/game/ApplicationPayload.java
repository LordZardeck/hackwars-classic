package game;

/**
 * Marker for typed payloads carried by {@link ApplicationData}.
 */
public interface ApplicationPayload {
    ApplicationCommand getCommand();
}
