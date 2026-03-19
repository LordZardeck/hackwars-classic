package com.hackwars.integration.expected;

import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExpectedFailureRuleTest {
    @Test
    public void tracks_expectedFailures_and_expectedPasses() {
        Result expectedFailureResult = JUnitCore.runClasses(ExpectedFailureTestCase.class);
        Result unexpectedPassResult = JUnitCore.runClasses(UnexpectedPassTestCase.class);

        assertTrue(expectedFailureResult.wasSuccessful());
        assertEquals(0, expectedFailureResult.getFailureCount());
        assertEquals(1, expectedFailureResult.getRunCount());

        assertEquals(1, unexpectedPassResult.getFailureCount());
        assertEquals(1, unexpectedPassResult.getRunCount());
        assertTrue(unexpectedPassResult.getFailures().get(0).getMessage().contains("passed unexpectedly"));
    }

    @RunWith(JUnit4.class)
    public static class ExpectedFailureTestCase extends com.hackwars.integration.IntegrationTestSupport {
        @Test
        @ExpectedFailure("known broken workflow")
        public void fails_and_is_recorded() {
            throw new AssertionError("boom");
        }
    }

    @RunWith(JUnit4.class)
    public static class UnexpectedPassTestCase extends com.hackwars.integration.IntegrationTestSupport {
        @Test
        @ExpectedFailure("known broken workflow")
        public void passes_unexpectedly() {
        }
    }
}
