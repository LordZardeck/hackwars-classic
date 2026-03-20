package com.hackwars.integration;

import assignments.LoginAssignment;
import assignments.LoginSuccessAssignment;
import assignments.PacketAssignment;
import assignments.PingAssignment;
import com.hackwars.rpc.RequestPage;
import com.plink.dolphinnet.MessageClient;
import util.Encryption;

import java.time.Duration;

public final class GameProtocolClient implements AutoCloseable {
    private final MessageClient messageClient;
    private final AssignmentInbox inbox = new AssignmentInbox();
    private LoginSuccessAssignment loginSuccess;

    public GameProtocolClient(IntegrationStackConfig config) {
        this.messageClient = new MessageClient(config.getAddress(), config.getGameOutPort(), 200000);
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
        throw new AssertionError("Timed out waiting for game reporter connection.");
    }

    public LoginSuccessAssignment login(String sessionTicket, Duration timeout) throws InterruptedException {
        Encryption.breakSingleton();
        Encryption.getInstance().init();

        LoginAssignment loginAssignment = new LoginAssignment(0, sessionTicket);
        loginAssignment.setPublicKey(Encryption.getInstance().getEncodedKey());
        messageClient.addFinishedAssignment(loginAssignment);

        loginSuccess = inbox.await(LoginSuccessAssignment.class, timeout);
        if (loginSuccess.getPublicKey() != null) {
            Encryption.getInstance().finalize(loginSuccess.getPublicKey());
        }
        return loginSuccess;
    }

    public PingAssignment ping(Duration timeout) throws InterruptedException {
        messageClient.addFinishedAssignment(new PingAssignment(0, loginSuccess.getIP()));
        return inbox.await(PingAssignment.class, timeout);
    }

    public PacketAssignment requestPage(Duration timeout) throws InterruptedException {
        messageClient.addFinishedAssignment(new RequestPage(loginSuccess.getEncryptedIP()).toRfc());
        return inbox.await(PacketAssignment.class, timeout, packet ->
            packet.getTitle() != null && packet.getBody() != null
        );
    }

    @Override
    public void close() {
        messageClient.clean();
    }
}
