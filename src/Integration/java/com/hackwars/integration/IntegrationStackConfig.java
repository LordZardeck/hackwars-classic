package com.hackwars.integration;

import java.io.IOException;
import java.net.ServerSocket;

public final class IntegrationStackConfig {
    private final String address;
    private final int gameInPort;
    private final int gameOutPort;
    private final int chatInPort;
    private final int chatOutPort;

    public IntegrationStackConfig(String address, int gameInPort, int gameOutPort, int chatInPort, int chatOutPort) {
        this.address = address;
        this.gameInPort = gameInPort;
        this.gameOutPort = gameOutPort;
        this.chatInPort = chatInPort;
        this.chatOutPort = chatOutPort;
    }

    public static IntegrationStackConfig allocate() {
        return new IntegrationStackConfig(
            "127.0.0.1",
            reservePort(),
            reservePort(),
            reservePort(),
            reservePort()
        );
    }

    private static int reservePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to reserve a free TCP port for integration tests.", e);
        }
    }

    public String getAddress() {
        return address;
    }

    public int getGameInPort() {
        return gameInPort;
    }

    public int getGameOutPort() {
        return gameOutPort;
    }

    public int getChatInPort() {
        return chatInPort;
    }

    public int getChatOutPort() {
        return chatOutPort;
    }
}
