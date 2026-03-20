package game;

/**
 * ApplicationData.java
 * <p>
 * Used to distribute function calls to the computers in the computer handling system.
 */

public class ApplicationData {

    public static final int INSIDE = 0;
    public static final int OUTSIDE = 1;
    private final int source;
    private final ApplicationCommand command;
    private final ApplicationPayload payload;
    private final int port;//The port that this ApplicationData should be delivered to.

    private final int sourcePort;//Port of the computer generating this function call.
    private final String sourceIP;//IP of the computer generating this request.

    public ApplicationData(ApplicationPayload payload, int port, String sourceIP) {
        this(payload, port, 0, sourceIP, INSIDE);
    }

    public ApplicationData(ApplicationPayload payload, int port, int sourcePort, String sourceIP, int source) {
        this(payload.getCommand(), payload, port, sourcePort, sourceIP, source);
    }

    private ApplicationData(
        ApplicationCommand command,
        ApplicationPayload payload,
        int port,
        int sourcePort,
        String sourceIP,
        int source
    ) {
        this.command = command;
        this.payload = payload;
        this.port = port;
        this.sourcePort = sourcePort;
        this.sourceIP = sourceIP == null ? "" : sourceIP;
        this.source = source;
    }

    /**
     Where does this message originate from?
     */
    public ApplicationData withSource(int source) {
        if (this.source == source) {
            return this;
        }
        return new ApplicationData(command, payload, port, sourcePort, sourceIP, source);
    }

    /**
     Get where the message comes from.
     */
    public int getSource() {
        return (source);
    }

    /**
     Returns the typed command identifier for this message.
     */
    public ApplicationCommand getCommand() {
        return (command);
    }

    /**
     getPort()
     returns the port that is being targeted with this function.
     */
    public int getPort() {
        return (port);
    }

    /**
     The IP that generated this ApplicationData.
     */
    public String getSourceIP() {
        return (sourceIP);
    }

    /**
     Set the port of the source of this application data.
     */
    public ApplicationData withSourcePort(int sourcePort) {
        if (this.sourcePort == sourcePort) {
            return this;
        }
        return new ApplicationData(command, payload, port, sourcePort, sourceIP, source);
    }

    /**
     Get the port of the source of this application data message.
     */
    public int getSourcePort() {
        return (sourcePort);
    }

    /**
     Returns the typed payload object.
     */
    public ApplicationPayload getPayload() {
        return (payload);
    }

    /**
     Returns the payload cast to the requested type.
     */
    public <T extends ApplicationPayload> T requirePayload(Class<T> payloadType) {
        if (payloadType.isInstance(payload)) {
            return payloadType.cast(payload);
        }
        String actual = payload == null ? "null" : payload.getClass().getName();
        throw new IllegalStateException(
            "Illegal payload provided for " + command.wireName() + ". Expected " + payloadType.getName() + ", Actual " + actual
        );
    }
}
