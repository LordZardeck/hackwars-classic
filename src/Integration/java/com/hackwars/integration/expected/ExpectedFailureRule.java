package com.hackwars.integration.expected;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public final class ExpectedFailureRule implements TestRule {
    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                ExpectedFailure expectedFailure = description.getAnnotation(ExpectedFailure.class);
                if (expectedFailure == null) {
                    base.evaluate();
                    return;
                }

                try {
                    base.evaluate();
                    ExpectedFailureTracker.recordUnexpectedPass(description.getDisplayName(), expectedFailure.reason());
                    throw new AssertionError("Expected failure passed unexpectedly: " + description.getDisplayName());
                } catch (Throwable throwable) {
                    ExpectedFailureTracker.recordExpectedFailure(description.getDisplayName(), expectedFailure.reason(), throwable);
                }
            }
        };
    }
}
