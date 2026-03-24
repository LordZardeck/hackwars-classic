package gui;
/**
 * FileProperties.java
 * this is the message window.
 */

import com.hackwars.state.GameState;
import game.EquipmentLicenseContent;
import game.FirewallSpecialAttribute;
import game.HackerFile;
import game.HackerFileInterop;
import game.NewFirewallContent;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.DecimalFormat;
import java.text.NumberFormat;
public class FileProperties extends Application {
    private Hacker hacker;
    private String fileName, folder;
    private HackerFile file;
    private JLabel name = new JLabel("-"), type = new JLabel("-"), maker = new JLabel("-"), price = new JLabel("-"), cpu = new JLabel("-"), quantity = new JLabel("-");
    private JTextArea description = new JTextArea("-");
    private JPanel panel = new JPanel(), otherPanel = new JPanel();
    private JTabbedPane tabbedPane = new JTabbedPane();

    public FileProperties(Hacker hacker, String fileName, String folder) {
        this.hacker = hacker;
        this.fileName = fileName;
        this.folder = folder;
        this.setTitle("File Properties -- " + fileName);
        this.setResizable(true);
        this.setMaximizable(false);
        this.setClosable(true);
        this.setIconifiable(true);
        this.addInternalFrameListener(this);
        setDefaultCloseOperation(JInternalFrame.HIDE_ON_CLOSE);
        setBounds(50, 50, 400, 325);
        hacker.setRequestedFile(Hacker.FILE_PROPERTIES);
        hacker.setFileProperties(this);
        GameState gameState = hacker.getView();
        String ip = hacker.getEncryptedIP();
        gameState.addFunctionCall(new com.hackwars.rpc.RequestFile(ip, folder, fileName).toRfc(0));
        setLayout(new MigLayout("fill"));
        panel.setLayout(new MigLayout("fill,wrap 2,align leading"));
        otherPanel.setLayout(new MigLayout("fill,wrap 2,align leading"));
        add(tabbedPane, "grow");
        populate();
        hacker.getPanel().add(this);
        setVisible(true);
        //System.out.println(MyHacker.getPanel().getComponentZOrder(this));
    }

    public void populate() {
        JLabel label = new JLabel("Loading...");
        panel.add(label);
        tabbedPane.addTab("General", panel);
    }

    public void receivedFile(HackerFile file) {
        this.file = file;
        panel.removeAll();
        otherPanel.removeAll();
        Font f = new Font("Dialog", Font.PLAIN, 12);
        JLabel nameLabel = new JLabel("Name: ");
        panel.add(nameLabel);
        panel.add(name);

        JLabel typeLabel = new JLabel("Type: ");
        panel.add(typeLabel);
        panel.add(type);

        JLabel makerLabel = new JLabel("Maker: ");
        panel.add(makerLabel);
        panel.add(maker);

        JLabel priceLabel = new JLabel("Price: ");
        panel.add(priceLabel);
        panel.add(price);

        JLabel cpuLabel = new JLabel("CPU Cost: ");
        panel.add(cpuLabel);
        panel.add(cpu);

        JLabel quantityLabel = new JLabel("Quantity: ");
        panel.add(quantityLabel);
        panel.add(quantity);

        JLabel descriptionLabel = new JLabel("Description: ");
        panel.add(descriptionLabel, "align leading");
        JScrollPane sp = new JScrollPane(description);
        panel.add(sp, "grow");

        name.setFont(f);
        type.setFont(f);
        maker.setFont(f);
        price.setFont(f);
        cpu.setFont(f);
        quantity.setFont(f);
        description.setFont(f);
        description.setColumns(14);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setEditable(false);
        name.setText(file.getName());
        type.setText(HackerFileInterop.displayName(file));
        maker.setText(file.getMaker());
        NumberFormat nf = NumberFormat.getCurrencyInstance();
        price.setText(nf.format(file.getPrice()));
        description.setText(file.getPublicDescription());
        cpu.setText("" + file.getCPUCost());
        quantity.setText("" + file.getQuantity());
        if (HackerFileInterop.isEquipmentLicense(file)) {
            tabbedPane.addTab("Advanced", getCardPanel(file));
        } else if (HackerFileInterop.isNewFirewall(file)) {
            tabbedPane.addTab("Advanced", getFirewallPanel(file));
        }
    }

    public JPanel getCardPanel(HackerFile file) {
        JPanel returnPanel = new JPanel();
        returnPanel.setLayout(new MigLayout("wrap 2,align leading"));
        EquipmentLicenseContent content = HackerFileInterop.equipmentContent(file);
        if (content == null) {
            return returnPanel;
        }
        float durability = 50.0f;
        float max = 50.0f;
        try {//In case it's hardware that hasn't had a durability set for some reason, e.g., old hardware.
            durability = Float.valueOf(content.getCurrentQuality());
            max = Float.valueOf(content.getMaxQuality());
        } catch (Exception e) {
        }
        returnPanel.add(new JLabel("Durability:"));
        returnPanel.add(new JLabel(durability + "/" + max));
        int quality1 = Integer.valueOf(content.getQuality0());
        int quality2 = Integer.valueOf(content.getQuality1());
        String values = content.getBonusData();
        String[] v = values.split("\\|");
        String value1 = v[0];
        String value2 = v[1];
        returnPanel.add(new JLabel("Attribute 1: "));
        returnPanel.add(new JLabel(value1));
        returnPanel.add(new JLabel("Attribute 2: "));
        returnPanel.add(new JLabel(value2));

        int dT = Equipment.DUCT_TAPE_COST[quality1] + Equipment.DUCT_TAPE_COST[quality2];
        int ge = Equipment.GERMANIUM_COST[quality1] + Equipment.GERMANIUM_COST[quality2];
        int si = Equipment.SILICON_COST[quality1] + Equipment.SILICON_COST[quality2];
        int ybco = Equipment.YBCO_COST[quality1] + Equipment.YBCO_COST[quality2];
        int pu = Equipment.PLUTONIUM_COST[quality1] + Equipment.PLUTONIUM_COST[quality2];
        returnPanel.add(new JLabel("Repair Cost:"));
        JPanel repairPanel = new JPanel();
        repairPanel.setLayout(new MigLayout());
        JLabel label = new JLabel(dT + "x", new ImageIcon("images/ducttape.png"), SwingConstants.TRAILING);
        label.setHorizontalTextPosition(SwingConstants.LEADING);
        repairPanel.add(label);
        if (ge > 0) {
            label = new JLabel(ge + "x", new ImageIcon("images/germanium.png"), SwingConstants.TRAILING);
            label.setHorizontalTextPosition(SwingConstants.LEADING);
            repairPanel.add(label);
        }
        if (si > 0) {
            label = new JLabel(si + "x", new ImageIcon("images/silicon.png"), SwingConstants.TRAILING);
            label.setHorizontalTextPosition(SwingConstants.LEADING);
            repairPanel.add(label);
        }
        if (ybco > 0) {
            label = new JLabel(ybco + "x", new ImageIcon("images/YBCO.png"), SwingConstants.TRAILING);
            label.setHorizontalTextPosition(SwingConstants.LEADING);
            repairPanel.add(label);
        }
        if (pu > 0) {
            label = new JLabel(pu + "x", new ImageIcon("images/plutonium.png"), SwingConstants.TRAILING);
            label.setHorizontalTextPosition(SwingConstants.LEADING);
            repairPanel.add(label);
        }
        returnPanel.add(repairPanel);

        //cardInfo.setText(d+"\n"+value1+"\n"+value2);
        //returnPanel.add(cardInfo);


        return returnPanel;

    }

    private JPanel getFirewallPanel(HackerFile file) {
        JPanel returnPanel = new JPanel();
        returnPanel.setLayout(new MigLayout("wrap 2,align leading"));
        NewFirewallContent content = HackerFileInterop.firewallContent(file);
        if (content == null) {
            return returnPanel;
        }

        String equipLevel = content.getEquipLevel();
        returnPanel.add(new JLabel("Firewall Level Required for Use: "));
        returnPanel.add(new JLabel(equipLevel));

        DecimalFormat format = new DecimalFormat("#.##");
        float abs = 0.0f;
        float percent = 0.0f;
        //if(type == PacketPort.BANKING){
        returnPanel.add(new JLabel("Bank Damage Allowed: "));
        abs = Float.parseFloat(content.getBankDamageModifier());
        percent = abs * 100;
        returnPanel.add(new JLabel(format.format(percent) + "%"));

        abs = Float.parseFloat(content.getAttackDamageModifier());
        percent = abs * 100;
        returnPanel.add(new JLabel("Attack Damage Allowed: "));
        returnPanel.add(new JLabel(format.format(percent) + "%"));

        abs = Float.parseFloat(content.getFtpDamageModifier());
        percent = abs * 100;
        returnPanel.add(new JLabel("FTP Damage Allowed: "));
        returnPanel.add(new JLabel(format.format(percent) + "%"));

        abs = Float.parseFloat(content.getRedirectDamageModifier());
        percent = abs * 100;
        returnPanel.add(new JLabel("Redirect Damage Allowed: "));
        returnPanel.add(new JLabel(format.format(percent) + "%"));

        abs = Float.parseFloat(content.getHttpDamageModifier());
        percent = abs * 100;
        returnPanel.add(new JLabel("HTTP Damage Allowed: "));
        returnPanel.add(new JLabel(format.format(percent) + "%"));

        returnPanel.add(new JLabel("Attack Damage: "));
        returnPanel.add(new JLabel(content.getAttackDamage()));

        //special attributes

        FirewallSpecialAttribute specials1 = content.getSpecialAttribute1();
        String sa1 = specials1.getShortDescription();
        String v1 = specials1.getValue();
        returnPanel.add(new JLabel("Special Attributes: "), "span,wrap");
        if (!sa1.equals("")) {

            float value = Float.parseFloat(v1);
            percent = (value) * 100;

            returnPanel.add(new JLabel(percent + sa1), "span,wrap");
        }


        FirewallSpecialAttribute specials2 = content.getSpecialAttribute2();
        String sa2 = specials2.getShortDescription();
        String v2 = specials2.getValue();

        if (!sa2.equals("")) {
            float value = Float.parseFloat(v2);
            percent = (value) * 100;
            returnPanel.add(new JLabel(percent + sa2), "span,wrap");
        }


        return returnPanel;

    }


    public void internalFrameClosed(InternalFrameEvent e) {
        //MyHacker.setMessageWindowOpen(false);
    }

    public void actionPerformed(ActionEvent e) {
    }

}
