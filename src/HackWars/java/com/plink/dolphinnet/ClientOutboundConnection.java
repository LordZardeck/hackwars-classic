package com.plink.dolphinnet;

import java.net.*;

/**
 * <b>DolphinNet<br />
 * Benjamin E. Coe (2006)</b><br /><br />
 * <p>
 * The Reporter (client) end implementation of an outbound server.
 */
public class ClientOutboundConnection extends OutboundConnection {
    int i = 0;

    /// ////////////////////
    // Constructor.
    ClientOutboundConnection(Object parent, Socket socket) {
        super(parent, socket);
    }

    /**
     * Get any objects that are currently in queue to be output on this connection.
     */
    public synchronized Object getOutObject(int id) {
        MessageClient parent = (MessageClient) getParent();
        Object o = parent.getAssignment();
        return (o);
    }

    /**
     * Perform and special initialization steps.
     */
    public void specialInit(Socket socket) {
    }
}