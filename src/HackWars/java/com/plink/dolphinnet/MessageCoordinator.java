package com.plink.dolphinnet;

/**
 * <b>DolphinNet<br />
 * Benjamin E. Coe (2006)</b><br /><br />
 * <p>
 * An IParty provides an abstract basis for managing a series of parallel calculations. You must implement
 * the returnAssignment() method to receive responses from the Editor (server). An IParty
 * attaches an ID to an Assignment and sends it to the Editor (server) for distribution. Upon receiving
 * a completed Assignment the IParty should use the ID and the instance variables of that class
 * to assemble a complete result.<br /><br />
 * <p>
 * Only one IParty can be associated with the Editor (server) at any given time. For this reason the creation
 * of some sort of harness makes sense for controling the flow of multiple IParty calculation using
 * one central server.
 */
abstract public class MessageCoordinator {
    //Data
    private MessageServer messageServer = null;

    /// //////////////////////////
    // Constructors.
    public MessageCoordinator() {
        this.messageServer = null;
    }

    /**
     * An editor represents the server for distributing assignments. And is required.
     */
    public MessageCoordinator(MessageServer messageServer) {
        this.messageServer = messageServer;
        messageServer.setMessageCoordinator(this);
    }

    /// //////////////////////////
    // Getters.
    public MessageServer getMessageServer() {
        return (messageServer);
    }
    /////////////////////////////
    // Setters.

    /**
     * An editor represents the server for distributing assignments. And is required.
     */
    public void setEditor(MessageServer messageServer) {
        this.messageServer = messageServer;
        messageServer.setMessageCoordinator(this);
    }
    /////////////////////////////
    // Methods.

    /**
     * Add an assignment to the Editor for processing.
     */
    public void addAssignment(Assignment a) throws Exception {
       throw new Exception("Deprecated. Use addAssignment(int clientId, Assignment assignment)");
    }


    /**
     * Add an assignment to a specific client for processing.
     */
    public void addAssignment(int clientId, Assignment assignment) {
        messageServer.addAssignment(clientId, assignment);
    }

    /**
     * Receive a completed assignment.
     */
    abstract public void returnAssignment(Assignment a);

    /**
     * Receive a failed assignment.
     */
    abstract public void failedAssignment(Assignment a);
}