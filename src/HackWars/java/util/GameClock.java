package util;

/**
 * Shared static time source for first-party application code.
 */
public final class GameClock {
    private static volatile FrozenTime frozenTime = null;

    private GameClock() {
    }

    public static long nowMillis() {
        FrozenTime current = frozenTime;
        return current != null ? current.millis : System.currentTimeMillis();
    }

    public static long nowNanos() {
        FrozenTime current = frozenTime;
        return current != null ? current.nanos : System.nanoTime();
    }

    public static synchronized void freezeForTest(long millis, long nanos) {
        frozenTime = new FrozenTime(millis, nanos);
    }

    public static synchronized void advanceForTest(long millisDelta, long nanosDelta) {
        FrozenTime current = frozenTime;
        if (current == null) {
            current = new FrozenTime(System.currentTimeMillis(), System.nanoTime());
        }
        frozenTime = new FrozenTime(current.millis + millisDelta, current.nanos + nanosDelta);
    }

    public static synchronized void resetForTest() {
        frozenTime = null;
    }

    private static final class FrozenTime {
        private final long millis;
        private final long nanos;

        private FrozenTime(long millis, long nanos) {
            this.millis = millis;
            this.nanos = nanos;
        }
    }
}
