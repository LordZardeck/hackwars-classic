package game;

/**
 * Shared port type codes used across client, server, and wire packets.
 */
public enum PortType {
    BANKING(0),
    FTP(1),
    ATTACK(2),
    HTTP(3),
    REDIRECT(4),
    SHIPPING(4);

    private final int code;

    PortType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static PortType fromCode(int code) {
        for (PortType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown port type code: " + code);
    }
}
