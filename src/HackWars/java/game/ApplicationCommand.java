package game;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canonical identifier for an application command routed through the game server.
 */
public record ApplicationCommand(String wireName) {
    private static final Map<String, ApplicationCommand> INTERNED = new ConcurrentHashMap<>();

    public ApplicationCommand {
        Objects.requireNonNull(wireName, "wireName");
    }

    public static ApplicationCommand of(String wireName) {
        return INTERNED.computeIfAbsent(wireName, ApplicationCommand::new);
    }

    @Override
    public String toString() {
        return wireName;
    }
}
