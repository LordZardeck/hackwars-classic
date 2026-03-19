package com.hackwars.integration.ui;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class SwingTestSupport {
    private SwingTestSupport() {
    }

    public static void runOnEdt(final Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(runnable);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for EDT work.", interruptedException);
        } catch (InvocationTargetException invocationTargetException) {
            throw new AssertionError("EDT work failed.", invocationTargetException.getCause());
        }
    }

    public static <T> T callOnEdt(final Callable<T> callable) {
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                return callable.call();
            } catch (Exception exception) {
                throw new AssertionError("EDT callable failed.", exception);
            }
        }
        final Object[] result = new Object[1];
        final Throwable[] error = new Throwable[1];
        runOnEdt(() -> {
            try {
                result[0] = callable.call();
            } catch (Throwable throwable) {
                error[0] = throwable;
            }
        });
        if (error[0] != null) {
            throw new AssertionError("EDT callable failed.", error[0]);
        }
        @SuppressWarnings("unchecked")
        T cast = (T) result[0];
        return cast;
    }

    public static <T> T waitFor(String description, Duration timeout, Supplier<T> supplier, Predicate<T> predicate) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            T value = supplier.get();
            if (predicate.test(value)) {
                return value;
            }
            try {
                Thread.sleep(25L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for " + description, interruptedException);
            }
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    public static <T extends Component> List<T> findComponents(Container root, Class<T> type) {
        List<T> matches = new ArrayList<T>();
        collect(root, type, matches);
        return matches;
    }

    private static <T extends Component> void collect(Component component, Class<T> type, List<T> matches) {
        if (type.isInstance(component)) {
            matches.add(type.cast(component));
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collect(child, type, matches);
            }
        }
    }

    public static void disposeAllWindows() {
        runOnEdt(() -> {
            for (Window window : Window.getWindows()) {
                window.dispose();
            }
        });
        shutdownHtmlHandlers();
    }

    private static void shutdownHtmlHandlers() {
        try {
            Class<?> htmlHandlerClass = Class.forName("browser.HtmlHandler");
            Field runField = htmlHandlerClass.getDeclaredField("run");
            runField.setAccessible(true);
            runField.setBoolean(null, false);
        } catch (Throwable ignored) {
        }
    }
}
