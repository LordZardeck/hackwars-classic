package gui;


import com.hackwars.state.GameState;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PortManagementDummyListener implements ActionListener {

    private int port;
    private Hacker MyHacker;
    private PortManagement MyPortManagement;

    public PortManagementDummyListener(int port, Hacker MyHacker, PortManagement MyPortManagement) {
        this.MyHacker = MyHacker;
        this.port = port;
        this.MyPortManagement = MyPortManagement;
        //System.out.println("Port: "+port+"   "+value);
    }

    public void actionPerformed(ActionEvent e) {
        GameState myGameState = MyHacker.getView();
        boolean dummy = MyPortManagement.getDummy(port);
        myGameState.addFunctionCall(new com.hackwars.rpc.SetDummyPort(MyHacker.getEncryptedIP(), new Integer(port), new Boolean(!dummy)).toRfc(0));
    }
}
		
