package com.hackwars.integration;

import com.hackwars.integration.expected.ExpectedFailureRule;
import com.hackwars.integration.expected.ExpectedFailureTracker;
import org.junit.Rule;

public abstract class IntegrationTestSupport {
    protected final ExpectedFailureTracker expectedFailureTracker = new ExpectedFailureTracker();

    @Rule
    public final ExpectedFailureRule expectedFailureRule = new ExpectedFailureRule(expectedFailureTracker);
}
