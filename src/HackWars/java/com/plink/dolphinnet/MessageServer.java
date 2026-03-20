package com.plink.dolphinnet;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.net.*;

/**
 * <b>DolphinNet<br />
 * Benjamin E. Coe (2006)</b><br /><br />
 * <p>
 * The Editor class serves as a server for distributing parallel tasks to the connected Reporters (Clients).
 * To add assignments to the Editor you must extend on the IParty class which receives results and
 * on the Assignment class which represents concrete calculations.
 */

public class MessageServer implements Runnable {
    private class ServerConnection extends DuplexConnection {
        private static final int MISSING_CLIENT_ID = -1;
        private int id = MISSING_CLIENT_ID;

        ServerConnection(Socket socket) {
            super(socket);
        }

        @Override
        public void connect(int socketTimeOut) {
            super.connect(socketTimeOut);

            if(!getConnectionClosed()) {
                id = registerClient(this);
            }
        }

        @Override
        public void close() {
            super.close();

            MessageServer.this.removeClient(id);
        }

        public synchronized void onReceiveObject(@NotNull Object data) {
            if (data instanceof Assignment) {
                MessageServer.this.returnAssignment((Assignment) data);
            } else if (data instanceof Integer) {
                MessageServer.this.killAll();
            }
        }
    }

    private static final int CONNECTION_SOCKET_TIMEOUT = 150000;

    //Data.
    private ArrayList assignments;
    private MessageCoordinator messageCoordinator = null;
    private volatile Thread t = null;
    private final HashMap<Integer, ServerConnection> connections = new HashMap<>();
    //Sockets Data.
    private ServerSocket SReceive = null;
    private int receiveSocket = 1011;
    private int socketTimeOut = 1000;
    private int clientIdCounter = 0;
    private volatile boolean running = false;

    public MessageServer(int socketTimeOut, int receiveSocket) {
        //Create the array that assignments will be posted to.
        assignments = new ArrayList();

        //Set instance variables based on constructor.
        this.socketTimeOut = socketTimeOut;
        this.receiveSocket = receiveSocket;

        start();
    }

    private void start() {
        //Create the server end sockets.
        try {
            SReceive = new ServerSocket(receiveSocket);
            SReceive.setSoTimeout(socketTimeOut);
        } catch (Exception e) {
            e.printStackTrace();
        }
        //Initialize the server thread.
        running = true;
        t = new Thread(this, "com/plink/dolphinnet/Editor");
        t.start();
    }

    /**
     * Add the current IParty that is to receive results from the Editor.
     */
    public void setMessageCoordinator(MessageCoordinator messageCoordinator) {
        this.messageCoordinator = messageCoordinator;
    }

    public void setMaxAssignments(int maxAssignments) {
    }

    public void setClientJobSize(int clientJobSize) {
    }

    /**
     * Send an assignment to a specific client.
     */
    public synchronized void addAssignment(int clientId, Assignment assignment) {
        connections.get(clientId).sendData(assignment);
    }

    /**
     * Return an assignment to the current MessageCoordinator.
     */
    public synchronized void returnAssignment(Assignment assignment) {
        if (messageCoordinator != null) {
            messageCoordinator.returnAssignment(assignment);
        }
    }

    /**
     * Remove a client from the server.
     */
    public synchronized void removeClient(int id) {
        connections.remove(id);
    }

    /**
     * Kill all the Assignments that are currently running.
     */
    public synchronized void killAll() {
        assignments.clear();
    }

    public synchronized int getBoundPort() {
        if (SReceive == null) {
            return (receiveSocket);
        }
        return (SReceive.getLocalPort());
    }

    public void close() {
        kill();
    }

    public void kill() {
        running = false;
        Thread moribund = t;
        t = null;

        if (moribund != null) {
            moribund.interrupt();
        }

        try {
            if (SReceive != null) {
                SReceive.close();
            }
        } catch (Exception e) {
        }

        ArrayList activeConnections = new ArrayList(connections.values());
        for (int i = 0; i < activeConnections.size(); i++) {
            ServerConnection connection = (ServerConnection) activeConnections.get(i);
            connection.close();
        }
        connections.clear();
        assignments.clear();
    }

    private synchronized int registerClient(ServerConnection connection) {
        int id = clientIdCounter++;
        connections.put(id, connection);
        // Inform the client of their connection id
        connection.sendData(id);
        return id;
    }

    /**
     * The run method listens for a connection on the canonical server port.
     */
    private int gCount = 0;

    public void run() {
        Thread thisThread = Thread.currentThread();
        while (running && thisThread == t) {
            try {
                if (gCount == 1000) {
                    System.out.println("Garbage collecting.");
                    System.runFinalization();
                    System.gc();
                    gCount = 0;
                } else
                    gCount++;

                Socket temp = SReceive.accept();
                ServerConnection connection = new ServerConnection(temp);
                connection.connect(CONNECTION_SOCKET_TIMEOUT);
                System.out.println("Creating Duplex Port.");
            } catch (SocketTimeoutException timeout) {
            } catch (SocketException socketClosed) {
                if (running) {
                    socketClosed.printStackTrace();
                }
            } catch (Exception e) {
            }

            try {
                Thread.sleep(5);
            } catch (Exception e) {

            }
        }
    }
}
