package gui;
/**
 * MessageWindow.java
 * this is the message window.
 */

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;

import assignments.*;
import view.*;

import java.text.*;
import java.math.*;

import browser.*;

import java.net.*;
import java.util.*;

public class Help extends Application implements TreeSelectionListener, ComponentListener {
    private JDesktopPane mainPanel = null;
    private Hacker MyHacker = null;
    private HtmlHandler helpPane;
    private JTree treePane;
    private JScrollPane sp;
    private BufferedImage back = ImageLoader.getImage("images/networkBack.png");

    public Help(String name, boolean resize, boolean max, boolean close, boolean iconify, JDesktopPane mainPanel, Hacker MyHacker) {
        this.setTitle(name);
        this.setResizable(true);
        this.setMaximizable(false);
        this.setClosable(close);
        this.setIconifiable(iconify);
        this.addInternalFrameListener(this);
        setDefaultCloseOperation(JInternalFrame.HIDE_ON_CLOSE);
        this.mainPanel = mainPanel;
        this.MyHacker = MyHacker;
        this.setLayout(null);
        addComponentListener(this);
        setBackground(Color.black);
        //this.setFrameIcon(ImageLoader.getImageIcon("images/calc.png"));
    }

    public void populate() {
        Insets insets = getInsets();
        Rectangle bounds = getBounds();

        DefaultMutableTreeNode help = new DefaultMutableTreeNode("Help");

        DefaultMutableTreeNode tutorials = new DefaultMutableTreeNode("Tutorials");

        DefaultMutableTreeNode apis = new DefaultMutableTreeNode("APIs");

        DefaultMutableTreeNode challenges = new DefaultMutableTreeNode("Challenges");

        createTutorialNodes(tutorials);
        createAPINodes(apis);
        createChallengesNodes(challenges);
        help.add(tutorials);
        help.add(apis);
        help.add(challenges);
/*		NetworkTreeCellRenderer renderer = new NetworkTreeCellRenderer();
		renderer.setLeafIcon(null);
		renderer.setOpenIcon(null);
		renderer.setClosedIcon(null);
		renderer.setBackgroundSelectionColor(Color.white);
		renderer.setBackgroundNonSelectionColor(Color.black);
		renderer.setTextSelectionColor(Color.black);
		renderer.setTextNonSelectionColor(Color.white);
		renderer.setBorderSelectionColor(Color.gray);*/
        treePane = new JTree(help);
        //treePane.setCellRenderer(renderer);
        treePane.setBackground(Color.black);
        treePane.setOpaque(false);
        treePane.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        treePane.addTreeSelectionListener(this);


        sp = new JScrollPane(treePane);
        add(sp);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);

        sp.setBounds(insets.left + 2, insets.top + 2, 200, bounds.height - insets.top - 38);

        helpPane = new HtmlHandler();
        //helpPane.createView();
        //helpPane.setBounds(0,0,bounds.width-insets.left-215,700);
        //sp2 = new JScrollPane(helpPane.getView());
        //sp2.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        //sp2.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(helpPane.getView());
        helpPane.getView().setBounds(insets.left + 203, insets.top + 2, bounds.width - insets.left - 215, bounds.height - insets.top - 38);
        helpPane.parseDocument(util.LegacyRemoteDefaults.unavailableHtml("Help unavailable", "Legacy help content is unavailable in this build."), this);
    }

    public void createTutorialNodes(DefaultMutableTreeNode top) {
        top.add(new DefaultMutableTreeNode("Unavailable"));
    }

    public void createChallengesNodes(DefaultMutableTreeNode top) {
        top.add(new DefaultMutableTreeNode("Unavailable"));
    }

    public void createAPINodes(DefaultMutableTreeNode top) {
        top.add(new DefaultMutableTreeNode("Unavailable"));
    }


    public void internalFrameIconified(InternalFrameEvent e) {
        JDesktopIcon DI = getDesktopIcon();
        DI.setBackground(new Color(41, 42, 41));

        JButton b = (JButton) DI.getUI().getAccessibleChild(DI, 0);
        JLabel l = (JLabel) DI.getUI().getAccessibleChild(DI, 1);
        //b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setForeground(Color.WHITE);

        //b.setBorderPainted(false);

        DI.getUI().update(DI.getGraphics(), DI);
        getDesktopIcon().setLocation(322, 272);
    }

    public void internalFrameClosed(InternalFrameEvent e) {
        //MyHacker.setMessageWindowOpen(false);
    }

    public void valueChanged(TreeSelectionEvent e) {
        //System.out.println("Tree Changed");
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePane.getLastSelectedPathComponent();
        if (node == null)
            return;
        Object o = node.getUserObject();
        if (o instanceof HelpFile) {
            HelpFile link = (HelpFile) o;
            //System.out.println(link.getLink());
            try {
                helpPane.parseDocument(new URL(link.getLink()), this);
                //helpPane.createView();

            } catch (Exception ex) {
                //ex.printStackTrace();
            }
        }


    }

    public void componentHidden(ComponentEvent e) {
    }

    public void componentMoved(ComponentEvent e) {

    }

    public void componentResized(ComponentEvent e) {
        Insets insets = getInsets();
        Rectangle bounds = getBounds();
        sp.setBounds(insets.left + 2, insets.top + 2, 200, bounds.height - insets.top - 38);
        helpPane.getView().setBounds(insets.left + 203, insets.top + 2, bounds.width - insets.left - 215, bounds.height - insets.top - 38);
        sp.repaint();
        helpPane.getView().repaint();
    }

    public void componentShown(ComponentEvent e) {

    }

    public void paintComponent(Graphics g) {
        g.setColor(new Color(0, 0, 0));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.drawImage(back, 0, 0, null);

    }

}
