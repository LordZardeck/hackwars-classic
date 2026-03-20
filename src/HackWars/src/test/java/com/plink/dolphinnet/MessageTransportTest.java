package com.plink.dolphinnet;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MessageTransportTest {
    private final List<MessageClient> clients = new ArrayList<MessageClient>();
    private final List<MessageServer> servers = new ArrayList<MessageServer>();

    @After
    public void tearDown() {
        for (int i = 0; i < clients.size(); i++) {
            clients.get(i).clean();
        }
        for (int i = 0; i < servers.size(); i++) {
            servers.get(i).kill();
        }
    }

    @Test
    public void clientReceivesAssignedIdAndServerAssignmentRunsLocally() throws Exception {
        MessageServer server = new MessageServer(25, 0);
        servers.add(server);
        RecordingCoordinator coordinator = new RecordingCoordinator(server);
        RecordingDataHandler dataHandler = new RecordingDataHandler();
        MessageClient client = createClient(server, dataHandler);

        waitForClientId(client, 0);

        coordinator.addAssignment(new DataAssignment(100, "game-state"));

        dataHandler.awaitData("game-state");
        Assert.assertEquals(0, client.getClientId());
    }

    @Test
    public void clientCanReturnAssignmentsToServerOverSameSocket() throws Exception {
        MessageServer server = new MessageServer(25, 0);
        servers.add(server);
        RecordingCoordinator coordinator = new RecordingCoordinator(server);
        RecordingDataHandler dataHandler = new RecordingDataHandler();
        MessageClient client = createClient(server, dataHandler);

        waitForClientId(client, 0);

        ResultAssignment outbound = new ResultAssignment(200, "pong");
        client.addFinishedAssignment(outbound);

        Assignment returned = coordinator.awaitReturnedAssignment();
        Assert.assertTrue(returned instanceof ResultAssignment);
        ResultAssignment result = (ResultAssignment) returned;
        Assert.assertEquals("pong", result.getPayload());
        Assert.assertEquals(0, result.getReporterID());
    }

    @Test
    public void directedAssignmentsReachOnlyTheTargetClient() throws Exception {
        MessageServer server = new MessageServer(25, 0);
        servers.add(server);
        RecordingCoordinator coordinator = new RecordingCoordinator(server);
        RecordingDataHandler firstHandler = new RecordingDataHandler();
        RecordingDataHandler secondHandler = new RecordingDataHandler();
        MessageClient firstClient = createClient(server, firstHandler);
        MessageClient secondClient = createClient(server, secondHandler);

        waitForClientId(firstClient, 0);
        waitForClientId(secondClient, 1);

        coordinator.addAssignment(1, new DataAssignment(300, "only-second"));

        secondHandler.awaitData("only-second");
        Thread.sleep(200L);
        Assert.assertFalse(firstHandler.hasData("only-second"));
    }

    @Test
    public void killAllStopsRunningAssignments() throws Exception {
        KillAwareAssignment.reset();

        MessageServer server = new MessageServer(25, 0);
        servers.add(server);
        RecordingCoordinator coordinator = new RecordingCoordinator(server);
        RecordingDataHandler dataHandler = new RecordingDataHandler();
        MessageClient client = createClient(server, dataHandler);

        waitForClientId(client, 0);

        coordinator.addAssignment(new KillAwareAssignment(400));
        KillAwareAssignment.awaitStarted();

        server.killAll();
        KillAwareAssignment.awaitKilled();
    }

    private MessageClient createClient(MessageServer server, RecordingDataHandler dataHandler) {
        MessageClient client = new MessageClient("127.0.0.1", server.getBoundPort(), 200);
        client.setDataHandler(dataHandler);
        clients.add(client);
        return (client);
    }

    private void waitForClientId(final MessageClient client, final int expectedId) throws Exception {
        waitForCondition(new Condition() {
            public boolean evaluate() {
                return client.getClientId() == expectedId;
            }
        }, "client ID " + expectedId);
    }

    private static void waitForCondition(Condition condition, String description) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.evaluate()) {
                return;
            }
            Thread.sleep(25L);
        }
        Assert.fail("Timed out waiting for " + description);
    }

    private interface Condition {
        boolean evaluate();
    }

    private static final class RecordingCoordinator extends MessageCoordinator {
        private final CountDownLatch returnedLatch = new CountDownLatch(1);
        private volatile Assignment returnedAssignment = null;

        RecordingCoordinator(MessageServer messageServer) {
            super(messageServer);
        }

        public void returnAssignment(Assignment a) {
            returnedAssignment = a;
            returnedLatch.countDown();
        }

        public void failedAssignment(Assignment a) {
        }

        Assignment awaitReturnedAssignment() throws Exception {
            Assert.assertTrue("Expected returned assignment", returnedLatch.await(5, TimeUnit.SECONDS));
            return (returnedAssignment);
        }
    }

    private static final class RecordingDataHandler implements DataHandler {
        private final List<Object> data = Collections.synchronizedList(new ArrayList<Object>());

        public void addData(Object o) {
            if (o != null) {
                data.add(o);
            }
        }

        public void resetData() {
            data.clear();
        }

        public Object getData(int id) {
            synchronized (data) {
                if (id < 0 || id >= data.size()) {
                    return (null);
                }
                return (data.get(id));
            }
        }

        public void addFinishedAssignment(Assignment A) {
        }

        void awaitData(final Object expected) throws Exception {
            waitForCondition(new Condition() {
                public boolean evaluate() {
                    return hasData(expected);
                }
            }, "data " + expected);
        }

        boolean hasData(Object expected) {
            synchronized (data) {
                return data.contains(expected);
            }
        }
    }

    private static final class DataAssignment extends Assignment {
        private final String payload;

        DataAssignment(int id, String payload) {
            super(id);
            this.payload = payload;
        }

        public Object execute(DataHandler DH) {
            finish();
            return (payload);
        }
    }

    private static final class ResultAssignment extends Assignment {
        private final String payload;

        ResultAssignment(int id, String payload) {
            super(id);
            this.payload = payload;
            finish();
        }

        public Object execute(DataHandler DH) {
            finish();
            return (payload);
        }

        String getPayload() {
            return (payload);
        }
    }

    private static final class KillAwareAssignment extends Assignment {
        private static final ConcurrentHashMap<Integer, CountDownLatch> STARTED = new ConcurrentHashMap<Integer, CountDownLatch>();
        private static final ConcurrentHashMap<Integer, CountDownLatch> KILLED = new ConcurrentHashMap<Integer, CountDownLatch>();

        KillAwareAssignment(int id) {
            super(id);
            STARTED.put(Integer.valueOf(id), new CountDownLatch(1));
            KILLED.put(Integer.valueOf(id), new CountDownLatch(1));
        }

        public Object execute(DataHandler DH) {
            STARTED.get(Integer.valueOf(getID())).countDown();
            while (!isFinished()) {
                try {
                    Thread.sleep(25L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            KILLED.get(Integer.valueOf(getID())).countDown();
            return (null);
        }

        static void reset() {
            STARTED.clear();
            KILLED.clear();
        }

        static void awaitStarted() throws Exception {
            Assert.assertTrue(STARTED.get(Integer.valueOf(400)).await(5, TimeUnit.SECONDS));
        }

        static void awaitKilled() throws Exception {
            Assert.assertTrue(KILLED.get(Integer.valueOf(400)).await(5, TimeUnit.SECONDS));
        }
    }
}
