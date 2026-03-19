package com.hackwars.integration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class IntegrationTestEnvironmentTest {
    @Test
    public void defaultFixtureAndStackConfig_areSafePlaceholders() {
        IntegrationTestEnvironment environment = IntegrationTestEnvironment.create(SeedScenario.CORE_GAMEPLAY);

        assertEquals(SeedScenario.CORE_GAMEPLAY, environment.getFixture().getScenario());
        assertFalse(environment.getStack().isStarted());
        assertEquals(3306, environment.getStack().getMysqlPort());
        assertEquals(10021, environment.getStack().getGameInPort());
        assertEquals(10020, environment.getStack().getGameOutPort());
        assertEquals(10026, environment.getStack().getChatInPort());
        assertEquals(10025, environment.getStack().getChatOutPort());
    }
}
