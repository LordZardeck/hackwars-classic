package gui;
/**
 * PortManagementMouseListener.java
 * this is the mouse listener for the port management window.
 */

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

import assignments.*;
import view.*;

import java.text.*;

public class ChallengeMouseListener implements MouseListener {
    //data
    private Hacker MyHacker;
    private int id;

    public ChallengeMouseListener(Hacker MyHacker, int id) {
        this.MyHacker = MyHacker;
        this.id = id;
    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    public void mousePressed(MouseEvent e) {

    }

    public void mouseReleased(MouseEvent e) {

    }

    public void mouseClicked(MouseEvent e) {
        // TODO: Removed legacy challenge help endpoint: /help/challenges.php?id=<id>
        MyHacker.showMessage("Challenge details are unavailable in this build.");
    }

}
