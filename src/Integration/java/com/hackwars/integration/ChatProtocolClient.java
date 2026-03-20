package com.hackwars.integration;

import assignments.LoginAssignment;
import assignments.PingAssignment;
import com.plink.dolphinnet.MessageClient;

import java.time.Duration;

public final class ChatProtocolClient implements AutoCloseable {
    private final MessageClient messageClient;
    private final AssignmentInbox inbox = new AssignmentInbox();

    public ChatProtocolClient(IntegrationStackConfig config) {
        this.messageClient = new MessageClient(config.getAddress(), 200000, config.getChatInPort(), config.getChatOutPort());
        this.messageClient.setDataHandler(inbox);
    }

    public void awaitConnected(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (messageClient.getID() != -1) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("Timed out waiting for chat reporter connection.");
    }

    public void login(String sessionTicket, byte[] publicKey) {
        LoginAssignment loginAssignment = new LoginAssignment(0, sessionTicket);
        loginAssignment.setPublicKey(publicKey);
        messageClient.addFinishedAssignment(loginAssignment);
    }

    public PingAssignment ping(String chatUser, Duration timeout) throws InterruptedException {
        messageClient.addFinishedAssignment(new PingAssignment(0, chatUser));
        return inbox.await(PingAssignment.class, timeout);
    }

    @Override
    public void close() {
        messageClient.clean();
    }
}
