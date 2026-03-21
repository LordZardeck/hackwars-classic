package gui;


import com.hackwars.state.GameState;
import util.LegacyRemoteDefaults;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class StoreActionListener implements ActionListener {

    private Hacker MyHacker;
    private String name, ip;
    private JSpinner spinner;
    private WebBrowser MyWebBrowser;

    public StoreActionListener(Hacker MyHacker, String name, JSpinner spinner, String ip, WebBrowser MyWebBrowser) {
        this.MyHacker = MyHacker;
        this.MyWebBrowser = MyWebBrowser;
        this.name = name;
        this.spinner = spinner;
        this.ip = ip;
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().equals("Buy")) {
            int quantity = (int) ((Integer) spinner.getValue());
            GameState myGameState = MyHacker.getView();
            // TODO: Removed legacy remote domain lookup endpoint: http://www.hackwars.net/xmlrpc/domain.php
            String result = LegacyRemoteDefaults.normalizeDomain(ip);
            myGameState.addFunctionCall(new com.hackwars.rpc.RequestPurchase(result, MyHacker.getEncryptedIP(), name, quantity).toRfc(Hacker.BROWSER));
            MyHacker.setRequestedDirectory(Hacker.BROWSER);
            //MyWebBrowser.removeProducts();
        }
    }
}
		
