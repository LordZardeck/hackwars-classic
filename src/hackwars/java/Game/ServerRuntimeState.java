package Game;

import util.Time;

public final class ServerRuntimeState {
    private static volatile Time clock = null;
    private static volatile boolean running = true;
    private static volatile long shutdownAt = 0L;
    private static volatile boolean testing = false;

    private ServerRuntimeState() {
    }

    public static void setClock(Time serverClock) {
        clock = serverClock;
    }

    public static long now() {
        Time currentClock = clock;
        if (currentClock == null) {
            return System.currentTimeMillis();
        }
        return currentClock.getCurrentTime();
    }

    public static void setRunning(boolean value) {
        running = value;
    }

    public static boolean isRunning() {
        return running;
    }

    public static void setShutdownAt(long value) {
        shutdownAt = value;
    }

    public static long getShutdownAt() {
        return shutdownAt;
    }

    public static void setTesting(boolean value) {
        testing = value;
    }

    public static boolean isTesting() {
        return testing;
    }
}
