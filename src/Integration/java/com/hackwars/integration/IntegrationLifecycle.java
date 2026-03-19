package com.hackwars.integration;

public interface IntegrationLifecycle extends AutoCloseable {
    void beforeAll() throws Exception;

    void beforeScenario(SeedScenario scenario) throws Exception;

    void afterScenario(SeedScenario scenario) throws Exception;

    @Override
    void close() throws Exception;
}
