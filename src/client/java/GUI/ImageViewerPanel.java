package gui;


import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

import view.*;

public class ImageViewerPanel extends JPanel {

    private aImage aI;

    public void setAImage(aImage aI) {
        this.aI = aI;
    }

    public void paintComponent(Graphics g) {
        if (aI != null)
            g.drawImage(aI.getBufferedImage(), 0, 0, this);
    }

}
