package com.plink.dolphinnet;

import java.net.*;

/**
 * <b>DolphinNet<br />
 * Benjamin E. Coe (2006)</b><br /><br />
 * <p>
 * The Editor (server) end implementation of an outbound server.
 */
public class ServerOutboundConnection extends OutboundConnection {
    /// ////////////////////
    // Constructor.
    ServerOutboundConnection(Object parent, Socket socket) {
        super(parent, socket);
    }

    /**
     * Get any objects that are currently in queue to be output on this connection.
     */
    public synchronized Object getOutObject(int id) {
        MessageServer parent = (MessageServer) getParent();
        Object o = parent.getAssignment(getID());

        return (o);
    }

    /**
     * Perform and special initialization steps.
     */
    public void specialInit(Socket socket) {
        //Get and distribute client IDs
        MessageServer parent = (MessageServer) getParent();
        setID(parent.getID());
        parent.addClient(getID());
        ClientBinaryList b = parent.getClients();
        ClientData c = (ClientData) b.get(new Integer(getID()));
        c.addJob(new Integer(getID()));
    }
}