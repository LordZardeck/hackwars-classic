package gui;
/**
 * MessageWindow.java
 * this is the message window.
 */

import javax.swing.*;
import java.awt.*;

public class NetworkInfoPanel extends JPanel {

    private NetworkPanel networkPanel;
    private JLabel nameLabel = new JLabel(), ipLabel = new JLabel(), questLabel = new JLabel();
    private JLabel questCountLabel = new JLabel(), shippingCountLabel = new JLabel(), regularCountLabel = new JLabel(), storeCountLabel = new JLabel();
    private JTree treePane;
    private JScrollPane sp;

    public NetworkInfoPanel(NetworkPanel networkPanel) {
        this.networkPanel = networkPanel;
        setLayout(new GridBagLayout());
        setBackground(MapPanel.NETWORK_INFO_BACKGROUND);
        //setPreferredSize(new Dimension(200,200));
        setOpaque(false);
        populate();
    }

    private void populate() {
        nameLabel.setText(networkPanel.getName());
        nameLabel.setFont(MapPanel.NETWORK_NAME_FONT);
        nameLabel.setForeground(MapPanel.NETWORK_NAME_COLOR);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.weightx = 0.0;
        c.weighty = 0.0;
        c.insets = new Insets(5, 5, 5, 5);
        c.fill = GridBagConstraints.BOTH;
        add(nameLabel, c);

        c.gridx = 0;


        storeCountLabel = new JLabel("Store (0)");
        storeCountLabel.setForeground(MapPanel.NETWORK_STORE_COLOR);
        storeCountLabel.setFont(MapPanel.NETWORK_QUESTS_FONT);
        c.gridy = 2;
        add(storeCountLabel, c);

        questCountLabel = new JLabel("Quests (0)");
        questCountLabel.setForeground(MapPanel.NETWORK_QUEST_COLOR);
        questCountLabel.setFont(MapPanel.NETWORK_QUESTS_FONT);
        c.gridy = 3;
        add(questCountLabel, c);

        shippingCountLabel = new JLabel("Shipping (0)");
        shippingCountLabel.setForeground(MapPanel.NETWORK_SHIPPING_COLOR);
        shippingCountLabel.setFont(MapPanel.NETWORK_QUESTS_FONT);
        c.gridy = 4;
        add(shippingCountLabel, c);

        regularCountLabel = new JLabel("Regular (0)");
        regularCountLabel.setForeground(MapPanel.NETWORK_REGULAR_COLOR);
        regularCountLabel.setFont(MapPanel.NETWORK_QUESTS_FONT);
        c.gridy = 5;
        add(regularCountLabel, c);

        c.gridx++;
        JLabel l = new JLabel("");
        c.weightx = 1.0;
        add(l, c);

        l = new JLabel("");
        c.gridy = 6;
        c.insets = new Insets(0, 0, 20, 0);
        c.weighty = 1.0;
        add(l, c);

    }

    public void setNetwork(String network) {
        nameLabel.setText(network);
        storeCountLabel.setText("Store (" + networkPanel.getStoreNpcCount() + ")");
        questCountLabel.setText("Quests (" + networkPanel.getQuestNpcCount() + ")");
        shippingCountLabel.setText("Shipping (" + networkPanel.getShippingNpcCount() + ")");
        regularCountLabel.setText("Regular (" + networkPanel.getRegularNpcCount() + ")");
    }

}
