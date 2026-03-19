package com.hackwars.integration;

public final class IntegrationTestEnvironment {
    private static final IntegrationTestEnvironment INSTANCE = new IntegrationTestEnvironment();

    private final SeedScenario scenario = SeedScenario.defaultScenario();
    private final IntegrationStackConfig config = IntegrationStackConfig.allocate();
    private final HackWarsStack stack = new HackWarsStack(config, scenario);

    private IntegrationTestEnvironment() {
    }

    public static IntegrationTestEnvironment shared() {
        return INSTANCE;
    }

    public synchronized HackWarsStack stack() {
        stack.start();
        return stack;
    }

    public SeedScenario scenario() {
        return scenario;
    }
}
