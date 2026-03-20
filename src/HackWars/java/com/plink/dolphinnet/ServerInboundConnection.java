package com.plink.dolphinnet;

import java.net.*;

/**
 * <b>DolphinNet<br />
 * Benjamin E. Coe (2006)</b><br /><br />
 * <p>
 * The Editor (server) end implementation of an inbound server.
 */
public class ServerInboundConnection extends InboundConnection {
    /// /////////////////////////
    //Constructor.
    public ServerInboundConnection(Object parent, Socket socket) {
        super(parent, socket);
        super.setThreadSleepTime(50);
    }

    /**
     * Dispatch a received object to the appropriate location.
     */
    public synchronized void putInObject(Object o) {
        MessageServer e = (MessageServer) this.getParent();

        if (o instanceof Assignment) {
            Assignment a = (Assignment) o;
            e.returnAssignment(a);
        }

        if (o instanceof Integer) {
            e.killAll();
        }
    }

    /**
     * Perform and special inizialization steps.
     */
    public void specialInit() {
    }
}