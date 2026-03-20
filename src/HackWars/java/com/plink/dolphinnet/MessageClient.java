package com.plink.dolphinnet;

import org.jetbrains.annotations.NotNull;

import java.net.*;
import java.util.ArrayList;

/**
 * <b>DolphinNet<br />
 * Benjamin E. Coe (2006)</b><br /><br />
 * <p>
 * The Reporter acts as the client and connects to a central server (Editor). It simply receives Assignments
 * and throws them into a runnable thread. It continually checks the threads for completion and
 * returns finished Assignments (instance variables having been filled out) to the Editor (server).
 * <br /><br />
 * The Future: Some method should be implemented for distributing new implementations of Assignments
 * to the Reporter. As of right now the Assignment class must be present on the client and server
 * side.
 */
public class MessageClient implements Runnable {
    private int id = -1;

    private DataHandler DH;
    private final ArrayList<RunAssignment> processes = new ArrayList<>();
    private volatile Thread t = null;
    private ClientConnection connection = null;
    private boolean killAllAssignments = false;

    private class ClientConnection extends DuplexConnection {
        ClientConnection(Socket socket) {
            super(socket);
        }

        public void onReceiveObject(@NotNull Object data) {
            try {
                if (data instanceof Assignment) {
                    MessageClient.this.addAssignment((Assignment) data);
                } else if (data instanceof Integer io) {
                    if (io > -1) {
                        MessageClient.this.setID(io);
                    } else {
                        MessageClient.this.killAllAssignments();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void clean() {
        kill();
    }

    //Our threading inner class.
    private class RunAssignment implements Runnable {
        private volatile Thread t;
        private Assignment assignment;
        private boolean finished = false;

        /// //////////
        //Constructor.
        RunAssignment(Assignment assignment) {
            this.assignment = assignment;
            t = new Thread(this);
            t.start();
        }

        /// //////////
        // Getters.
        public Assignment getAssignment() {
            return (assignment);
        }

        public boolean isFinished() {
            return (finished);
        }

        /// //////////
        //Methods.
        public void kill() {
            finished = true;
            t = null;
            assignment.kill();
        }

        public void run() {
            Thread thisThread = Thread.currentThread();
            while (thisThread == t) {
                Object T = null;
                if (!finished)
                    T = (Object) assignment.execute(DH);
                if (DH != null)
                    DH.addData(T);
                assignment.setReporterID(getID());

                kill();

                try {
                    Thread.sleep(100);
                } catch (Exception e) {

                }
            }
        }
    }

    public int getID() {
        return id;
    }

    public void setID(int id) {
        this.id = id;
    }

    public void setDataHandler(DataHandler DH) {
        this.DH = DH;
    }

    public void kill() {
        t = null;
        if (connection != null) {
            connection.close();
            connection = null;
        }
        DH = null;
        processes.clear();
    }

    /**
     * Kill all the assignments that are currently running.
     */
    public synchronized void killAllAssignments() {
        killAllAssignments = true;
    }

    public MessageClient(String address, int port, int socketTimeOut) {
        //Set up the server.
        try {
            connection = new ClientConnection(new Socket(address, port));
            connection.connect(socketTimeOut);
        } catch (Exception e) {
        }

        //Set up the execution thread.
        t = new Thread(this);
        t.start();
    }

    public void run() {
        Thread thisThread = Thread.currentThread();
        while (thisThread == t) {
            if (processes == null) {
                break;
            }
            if (killAllAssignments) {
                for (int i = 0; i < processes.size(); i++) {
                    RunAssignment temp = (RunAssignment) processes.get(i);
                    temp.kill();
                    Assignment A = temp.getAssignment();
                    //FinishedAssignments.add(A);
                    processes.remove(i);
                    break;
                }
                killAllAssignments = false;
            }
            for (int i = 0; i < processes.size(); i++) {
                RunAssignment temp = (RunAssignment) processes.get(i);
                if (temp != null)
                    if (temp.isFinished()) {
                        temp.kill();
                        Assignment A = temp.getAssignment();
                        //FinishedAssignments.add(new ZippedAssignment(0,A));
                        processes.remove(i);
                        break;
                    }
            }
            try {
                Thread.sleep(100);
            } catch (Exception e) {

            }
        }
        DH = null;
    }

    /**
     * Adds an assignment to the assignment list for execution.
     */
    public synchronized void addAssignment(Assignment assignment) {
        try {
            if (processes == null) {
                return;
            }
            assignment.setReporterID(getID());
            processes.add(new RunAssignment(assignment));
        } catch (Exception e) {
            //	e.printStackTrace();
        }
    }

    /**
     * Add an external finished assignment to the list.
     */
    public synchronized void addFinishedAssignment(Assignment assignment) {
        assignment.setReporterID(getID());
        connection.sendData(assignment);
    }
}
