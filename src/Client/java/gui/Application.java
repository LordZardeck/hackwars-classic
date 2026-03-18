package gui;
/**
 * Base class for all in game applications.
 *
 * @author Cameron McGuinness
 */

import javax.swing.*;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class Application extends JInternalFrame implements ActionListener, MouseListener, InternalFrameListener {

    //public abstract void populate();
    public static Hacker MyHacker;

    public static void setHacker(Hacker hacker) {
        MyHacker = hacker;
    }


    public void actionPerformed(ActionEvent e) {

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

    }

    public void internalFrameClosing(InternalFrameEvent e) {
    }

    public void internalFrameClosed(InternalFrameEvent e) {
    }

    public void internalFrameOpened(InternalFrameEvent e) {
    }

    public void internalFrameIconified(InternalFrameEvent e) {
        MyHacker.addMinimizedFrame(getDesktopIcon());
    }

    public void internalFrameDeiconified(InternalFrameEvent e) {
        MyHacker.removeMinimizedFrame(getDesktopIcon());
        try {
            setIcon(false);
        } catch (Exception ex) {
        }
        MyHacker.getPanel().add(this);
        moveToFront();
    }

    public void internalFrameActivated(InternalFrameEvent e) {

    }

    public void internalFrameDeactivated(InternalFrameEvent e) {

    }

    public int showYesCancelDialog(String title, String message) {
        Object[] options = {"Yes", "Cancel"};
        return (showOptionsDialog(title, message, options));
    }

    public int showYesNoDialog(String title, String message) {
        Object[] options = {"Yes", "No"};
        return (showOptionsDialog(title, message, options));
    }

    public int showOptionsDialog(String title, String message, Object[] options) {
        int n = JOptionPane.showOptionDialog(this, message, title,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[1]);
        return (n);
    }

    public int showDeleteDialog(String message) {
        return (showYesNoDialog("Delete?", "Are you sure you want to permanently delete " + message + "?"));
    }

    public int showSellDialog(String message) {
        return (showYesNoDialog("Sell all files?", "Are you sure you want to sell these " + message + " files?  All quantity of each file will be sold."));
    }

}
