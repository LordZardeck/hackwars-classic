package com.hackwars.integration;

import assignments.LoginSuccessAssignment;
import assignments.PacketAssignment;
import assignments.PingAssignment;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OfflineStackProtocolIntegrationTest {
    @Test
    public void offlineStack_supports_gameLogin_chatPing_andPageRequest() throws Exception {
        HackWarsStack stack = IntegrationTestEnvironment.shared().stack();
        SeedScenario scenario = stack.getScenario();

        try (GameProtocolClient gameClient = new GameProtocolClient(stack.getConfig());
             ChatProtocolClient chatClient = new ChatProtocolClient(stack.getConfig())) {
            gameClient.awaitConnected(Duration.ofSeconds(5));
            chatClient.awaitConnected(Duration.ofSeconds(5));

            LoginSuccessAssignment login = gameClient.login(scenario.getAccount().getSessionTicket(), Duration.ofSeconds(8));
            assertEquals(scenario.getPlayerIp(), login.getIP());
            assertNotNull(login.getEncryptedIP());

            chatClient.login(scenario.getAccount().getSessionTicket(), login.getPublicKey());

            PingAssignment gamePing = gameClient.ping(Duration.ofSeconds(5));
            assertNotNull(gamePing);

            PingAssignment chatPing = chatClient.ping(scenario.getAccount().getPlayFabId().toLowerCase(), Duration.ofSeconds(5));
            assertNotNull(chatPing);

            PacketAssignment pagePacket = gameClient.requestPage(Duration.ofSeconds(8));
            assertEquals(scenario.getWebsiteTitle(), pagePacket.getTitle());
            assertTrue(pagePacket.getBody().contains("Hello from integration web page."));
        }
    }
}
