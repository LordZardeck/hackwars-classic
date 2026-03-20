package com.plink.dolphinnet;

import com.plink.dolphinnet.assignments.ZippedAssignment;
import com.plink.dolphinnet.util.BinaryList;
import org.junit.Assert;
import org.junit.Test;

import java.io.Serializable;

public class DolphinNetCoreTest {
    @Test
    public void assignmentLifecycleAndHashRemainStable() {
        LifecycleAssignment assignment = new LifecycleAssignment(10, "payload");

        Assert.assertFalse(assignment.isFinished());
        Assert.assertEquals(HashSingleton.getHash(), assignment.getHash());

        assignment.execute(null);
        Assert.assertTrue(assignment.isFinished());

        assignment = new LifecycleAssignment(11, "payload");
        assignment.kill();
        Assert.assertTrue(assignment.isFinished());
    }

    @Test
    public void zippedAssignmentRoundTripsWrappedAssignment() {
        LifecycleAssignment assignment = new LifecycleAssignment(20, "zip-me");
        assignment.setReporterID(7);

        ZippedAssignment zipped = new ZippedAssignment(21, assignment);
        Assignment unzipped = zipped.getAssignment();

        Assert.assertTrue(unzipped instanceof LifecycleAssignment);
        LifecycleAssignment restored = (LifecycleAssignment) unzipped;
        Assert.assertEquals(20, restored.getID());
        Assert.assertEquals(7, restored.getReporterID());
        Assert.assertEquals("zip-me", restored.getPayload());
    }

    @Test
    public void baseBinaryListSupportsClearAndDuplicateRejection() {
        StringBinaryList list = new StringBinaryList();

        Assert.assertTrue(list.add("bravo"));
        Assert.assertTrue(list.add("alpha"));
        Assert.assertTrue(list.add("alpha"));
        Assert.assertEquals("alpha", list.get("alpha"));
        Assert.assertEquals("bravo", list.get("bravo"));

        list.clear();
        Assert.assertNull(list.get("alpha"));
        Assert.assertEquals("", list.toString());
    }

    @Test
    public void messageCoordinatorAttachesToServerAndQueuesAssignments() throws Exception {
        MessageServer server = new MessageServer(25, 0);
        try {
            TrackingCoordinator coordinator = new TrackingCoordinator(server);

            Assert.assertSame(server, coordinator.getMessageServer());

            LifecycleAssignment queued = new LifecycleAssignment(30, "queued");
            coordinator.addAssignment(queued);

            coordinator.setEditor(server);
            coordinator.addAssignment(new LifecycleAssignment(31, "queued-again"));
            coordinator.failedAssignment(queued);
            Assert.assertSame(queued, coordinator.lastFailed);
        } finally {
            server.kill();
        }
    }

    @Test
    public void hashSingletonReturnsStableSingletonAndHash() {
        HashSingleton singleton = HashSingleton.getInstance();
        Assert.assertSame(singleton, HashSingleton.getInstance());
        Assert.assertEquals(HashSingleton.getHash(), HashSingleton.getInstance().getHash());
        Assert.assertEquals(10, singleton.getRandomKey().length());
    }

    @Test
    public void messageServerSupportsBoundPortAndClose() {
        MessageServer server = new MessageServer(25, 0);
        try {
            Assert.assertTrue(server.getBoundPort() > 0);
        } finally {
            server.close();
        }
    }

    private static final class LifecycleAssignment extends Assignment implements Serializable {
        private final String payload;

        LifecycleAssignment(int id, String payload) {
            super(id);
            this.payload = payload;
        }

        public Object execute(DataHandler DH) {
            finish();
            return (payload);
        }

        String getPayload() {
            return (payload);
        }
    }

    private static final class TrackingCoordinator extends MessageCoordinator {
        private Assignment lastFailed = null;

        TrackingCoordinator(MessageServer messageServer) {
            super(messageServer);
        }

        public void returnAssignment(Assignment a) {
        }

        public void failedAssignment(Assignment a) {
            lastFailed = a;
        }
    }

    private static final class StringBinaryList extends BinaryList {
        public Object getKey(Object o) {
            return (o);
        }

        public int compare(Object o1, Object o2) {
            String s1 = (String) o1;
            String s2 = (String) o2;
            return (s1.compareTo(s2));
        }

        public String toString() {
            return ("");
        }
    }
}
