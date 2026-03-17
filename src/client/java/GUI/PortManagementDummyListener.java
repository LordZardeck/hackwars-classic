package gui;


import java.awt.event.*;

import assignments.*;
import com.hackwars.state.View;

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
        View MyView = MyHacker.getView();
        boolean dummy = MyPortManagement.getDummy(port);
        Object objects[] = {MyHacker.getEncryptedIP(), new Integer(port), new Boolean(!dummy)};
        MyView.setFunction("setdummyport");
        MyView.addFunctionCall(new RemoteFunctionCall(0, "setdummyport", objects));
    }
}
		
