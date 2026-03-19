package com.hackwars.integration.expected;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public final class ExpectedFailureTracker {
    private static final Object LOCK = new Object();

    private ExpectedFailureTracker() {
    }

    public static void recordExpectedFailure(String testName, String reason, Throwable throwable) {
        append(new ExpectedFailureRecord(
            "EXPECTED_FAILURE",
            testName,
            reason,
            throwable.getClass().getSimpleName() + ": " + String.valueOf(throwable.getMessage())
        ));
    }

    public static void recordUnexpectedPass(String testName, String reason) {
        append(new ExpectedFailureRecord("UNEXPECTED_PASS", testName, reason, ""));
    }

    private static void append(ExpectedFailureRecord record) {
        String path = System.getProperty("hackwars.clientUi.expectedFailuresFile");
        if (path == null || path.trim().isEmpty()) {
            return;
        }

        synchronized (LOCK) {
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
                writer.write(record.toTsv());
                writer.write(System.lineSeparator());
            } catch (IOException ioException) {
                throw new IllegalStateException("Unable to write expected-failure report to " + path, ioException);
            }
        }
    }
}
