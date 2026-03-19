package com.hackwars.integration;

import com.hackwars.client.ClientAuthGateway;
import com.hackwars.client.DeterministicClientAuthAccount;
import com.hackwars.client.DeterministicClientAuthGateway;
import com.plink.dolphinnet.Editor;
import game.computer.session.ComputerSessionOverrides;
import org.jetbrains.annotations.NotNull;
import server.ChatServer;
import util.PlayFabTokenVerifier;
import util.SessionTokenVerifier;
import util.SessionTokenVerifiers;

import java.util.Collections;

public final class HackWarsStack implements AutoCloseable {
    private final IntegrationStackConfig config;
    private final SeedScenario scenario;
    private Editor gameEditor;
    private Editor chatEditor;
    private server.HackerServer gameServer;
    private ChatServer chatServer;
    private boolean started;

    public HackWarsStack(IntegrationStackConfig config, SeedScenario scenario) {
        this.config = config;
        this.scenario = scenario;
    }

    public synchronized void start() {
        if (started) {
            return;
        }

        System.setProperty("hackwars.gameServer.address", config.getAddress());
        System.setProperty("hackwars.gameServer.inPort", String.valueOf(config.getGameInPort()));
        System.setProperty("hackwars.gameServer.outPort", String.valueOf(config.getGameOutPort()));
        System.setProperty("hackwars.chatServer.address", config.getAddress());
        System.setProperty("hackwars.chatServer.inPort", String.valueOf(config.getChatInPort()));
        System.setProperty("hackwars.chatServer.outPort", String.valueOf(config.getChatOutPort()));
        System.setProperty("hackwars.remoteXmlRpc", "false");

        SessionTokenVerifiers.install(new SessionTokenVerifier() {
            @Override
            public PlayFabTokenVerifier.AuthResult verify(String accessToken) {
                PlayFabTokenVerifier.AuthResult result = scenario.authResultForSession(accessToken);
                if (result == null) {
                    throw new IllegalArgumentException("Unknown test session ticket: " + accessToken);
                }
                return result;
            }
        });
        ComputerSessionOverrides.installLocalSaveOverride((ip, active) -> scenario.getPlayerIp().equals(ip) ? scenario.getSaveXml() : null);

        gameEditor = new Editor(2048, 1000, config.getGameOutPort(), config.getGameInPort());
        gameEditor.setClientJobSize(4);
        gameServer = new server.HackerServer(gameEditor, "integration");

        chatEditor = new Editor(2048, 1000, config.getChatOutPort(), config.getChatInPort());
        chatEditor.setClientJobSize(4);
        chatServer = new ChatServer(chatEditor);

        started = true;
        try {
            Thread.sleep(250L);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    public IntegrationStackConfig getConfig() {
        return config;
    }

    public SeedScenario getScenario() {
        return scenario;
    }

    public ClientAuthGateway createAuthGateway() {
        DeterministicClientAuthAccount account = scenario.getAccount();
        return new DeterministicClientAuthGateway(Collections.singletonMap(account.getEmail(), account));
    }

    @Override
    public synchronized void close() {
        SessionTokenVerifiers.reset();
        ComputerSessionOverrides.reset();
    }
}
