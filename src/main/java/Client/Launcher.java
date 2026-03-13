package Client;
/**
 * Launcher.java
 * <p>
 * Performs the loading step necessary to bootstrap the GUI.
 */

import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.awt.image.BufferedImage;

import View.*;

import javax.imageio.*;
import java.net.URL;

import GUI.*;
import util.XmlRpcProxy;

public class Launcher extends JPanel implements ActionListener {
    private final String checksum = "dec 19";
    private static final String DEFAULT_SERVER_HOST = "127.0.0.1";
    private static final String DEFAULT_PLAYER_IP = "192.168.2.002";
    private boolean LoaderSet = false;
    private String ip = DEFAULT_SERVER_HOST;
    private boolean remoteAuth = false;
    private String checkDateRpcURL = "";
    private String loginRpcURL = "";
    private String loginBackgroundURL = "";
    private String fallbackPlayerIP = DEFAULT_PLAYER_IP;
    private JLabel message = null;
    private JPanel LoginFrame = null;
    private JTextField usernameField, ipField;
    private View MyView = null;
    private JButton button = null;
    private JProgressBar JPB = null;
    private JLabel label = null;
    private JPanel panel = null;
    private String clientDate = "Jan 9th, 2010 @ 8:00 PM";

    public Launcher() {
        setPreferredSize(new Dimension(250, 250));
    }

    public void init() {
        if (LoaderSet) {
            return;
        }
        LoaderSet = true;
        setBackground(new Color(255, 255, 255));
        setLayout(null);
        loadConfiguration();
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        createLoginWidget();
        getFiles(JPB);
        reconnect();
    }

    public void stop() {
        if (MyView != null)
            MyView.clean();
    }

    private void loadConfiguration() {
        ip = getConfigValue("serverHost", "hackwars.server.host", DEFAULT_SERVER_HOST);
        fallbackPlayerIP = getConfigValue("playerIP", "hackwars.player.ip", DEFAULT_PLAYER_IP);
        remoteAuth = "true".equalsIgnoreCase(getConfigValue("remoteAuth", "hackwars.remoteAuth", "false"));
        checkDateRpcURL = getConfigValue("checkDateRpcURL", "hackwars.checkDateRpcURL", "http://" + ip + "/xmlrpc/checkdate.php");
        loginRpcURL = getConfigValue("loginRpcURL", "hackwars.loginRpcURL", "http://" + ip + "/xmlrpc/loginrpc.php");
        loginBackgroundURL = getConfigValue("loginBackgroundURL", "hackwars.loginBackgroundURL", "");
    }

    private String getConfigValue(String configKey, String systemProperty, String fallback) {
        String value = null;
        try {
            value = System.getProperty(configKey);
        } catch (SecurityException e) {
        }
        if (value == null || value.trim().length() == 0) {
            try {
                value = System.getProperty(systemProperty);
            } catch (SecurityException e) {
            }
        }
        if (value == null || value.trim().length() == 0) {
            value = fallback;
        }
        return (value.trim());
    }

    private BufferedImage loadLoginBackground() {
        try {
            File local = new File("images/loginback.png");
            if (local.exists()) {
                return (ImageIO.read(local));
            }
        } catch (Exception e) {
        }
        try {
            InputStream in = Launcher.class.getClassLoader().getResourceAsStream("images/loginback.png");
            if (in != null) {
                BufferedImage image = ImageIO.read(in);
                in.close();
                return (image);
            }
        } catch (Exception e) {
        }
        if (loginBackgroundURL.length() > 0) {
            try {
                return (ImageIO.read(new URL(loginBackgroundURL)));
            } catch (Exception e) {
            }
        }
        return (null);
    }

    public void createLoginWidget() {
        LoginFrame = new JPanel();
        LoginFrame.setSize(250, 150);
        LoginFrame.setLocation(5, 50);
        SpringLayout layout = new SpringLayout();
        LoginFrame.setLayout(null);

        int height = 8;
        panel = new JPanel();
        panel.setLayout(layout);
        BufferedImage loginBackground = loadLoginBackground();
        if (loginBackground != null) {
            panel.setBorder(new CentredBackgroundBorder(loginBackground, panel));
        }
        panel.validate();
        message = new JLabel("<html><font color=\"#FF0000\">Invalid Username/Password. <br>Please Try Again.</font></html>");
        message.setVisible(false);
        panel.add(message);
        layout.putConstraint(SpringLayout.NORTH, message, height, SpringLayout.NORTH, panel);
        layout.putConstraint(SpringLayout.WEST, message, 22, SpringLayout.WEST, panel);
        height += message.getPreferredSize().height + 5;

        Object[] response = null;
        if (remoteAuth) {
            try {
                Object[] params = {clientDate};
                response = (Object[]) XmlRpcProxy.execute(checkDateRpcURL, "login", params);
                if (response != null && response.length > 1 && response[0] instanceof Boolean && ((Boolean) response[0]).booleanValue() == false && response[1] instanceof String) {
                    setMessage((String) response[1]);
                }
            } catch (Exception ex) {
                setMessage("<html><font color=\"#FF6600\">Remote auth unavailable, using local mode.</font></html>");
            }
        }
        label = new JLabel("Username: ");
        panel.add(label);
        layout.putConstraint(SpringLayout.NORTH, label, height, SpringLayout.NORTH, panel);
        layout.putConstraint(SpringLayout.WEST, label, 22, SpringLayout.WEST, panel);
        int width = label.getPreferredSize().width + 32;

        usernameField = new JTextField("", 10);
        panel.add(usernameField);
        layout.putConstraint(SpringLayout.NORTH, usernameField, height, SpringLayout.NORTH, panel);
        layout.putConstraint(SpringLayout.WEST, usernameField, width, SpringLayout.WEST, panel);
        height += usernameField.getPreferredSize().height + 5;

        label = new JLabel("Password: ");
        panel.add(label);
        layout.putConstraint(SpringLayout.NORTH, label, height, SpringLayout.NORTH, panel);
        layout.putConstraint(SpringLayout.WEST, label, 22, SpringLayout.WEST, panel);

        ipField = new JPasswordField("", 10);
        ipField.addActionListener(this);
        ipField.setActionCommand("Login");
        panel.add(ipField);
        layout.putConstraint(SpringLayout.NORTH, ipField, height, SpringLayout.NORTH, panel);
        layout.putConstraint(SpringLayout.WEST, ipField, width, SpringLayout.WEST, panel);
        height += ipField.getPreferredSize().height + 5;

        button = new JButton("Login");
        panel.add(button);
        button.addActionListener(this);
        layout.putConstraint(SpringLayout.NORTH, button, height, SpringLayout.NORTH, panel);
        layout.putConstraint(SpringLayout.WEST, button, width, SpringLayout.WEST, panel);
        height += button.getPreferredSize().height + 5;

        JLabel date = new JLabel(clientDate);
        Font f = new Font("dialog", Font.BOLD, 10);
        date.setFont(f);
        panel.add(date);
        layout.putConstraint(SpringLayout.NORTH, date, 145 - date.getPreferredSize().height, SpringLayout.NORTH, panel);
        layout.putConstraint(SpringLayout.WEST, date, 240 - date.getPreferredSize().width, SpringLayout.WEST, panel);

        LoginFrame.add(panel);
        panel.setBounds(0, 0, 250, 150);
        LoginFrame.setVisible(true);

        this.add(LoginFrame);
    }

    /**
     Download any files that might currently be needed.
     */
    private int imageCount = 86;

    public void getFiles(JProgressBar JPB) {
        //System.out.println("Getting Images");
        if (JPB == null)
            JPB = new JProgressBar();
        panel.setVisible(false);
        JPB.setVisible(true);

        JPB.setIndeterminate(false);
        JPB.setStringPainted(true);

        JPB.setString("Downloading Images 0%");
        JPB.setMinimum(0);
        JPB.setMaximum(imageCount);
        boolean download = false;
        //Check to see whether we have the images directory.
        String tmpDir = "";
        try {
            tmpDir = System.getProperty("java.io.tmpdir");
        } catch (SecurityException e) {
        }
        File imageDir = (tmpDir != null && tmpDir.trim().length() > 0) ? new File(tmpDir, "images") : new File("images");
        File CF = new File(imageDir, "checksum.txt");
        boolean canUseFileCache = true;
        try {
            if (CF.exists()) {
                //System.out.println("File Exists");
                try {
                    BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(CF)));
                    String check = "";
                    try {
                        check = in.readLine().toLowerCase();
                    } catch (Exception e) {
                        download = true;
                    }
                    //System.out.println("|"+check.length()+"|   |"+checksum.length()+"|");
                    if (!(check.equals(checksum))) {
                        //System.out.println("Not Equal");
                        download = true;
                    }
                    in.close();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                //System.out.println("File Does not Exist");
                download = true;
            }
        } catch (SecurityException e) {
            canUseFileCache = false;
        }
        if (canUseFileCache && download) {//Download the image pack.
            try {
                imageDir.mkdirs();
                BufferedWriter out = new BufferedWriter(new FileWriter(CF));
                out.write(checksum);
                out.close();
            } catch (Exception e) {
            }
        }
        JPB.setIndeterminate(true);
        JPB.setString("Initializing Classes");
        JPB.setVisible(false);
        panel.setVisible(true);
    }

    public void linkGo(URL link) {
        if (link == null) {
            return;
        }
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(link.toURI());
                return;
            }
        } catch (Exception e) {
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.indexOf("win") >= 0) {
                Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", link.toString()});
            } else if (os.indexOf("mac") >= 0) {
                Runtime.getRuntime().exec(new String[]{"open", link.toString()});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", link.toString()});
            }
        } catch (Exception e) {
        }
    }

    //Action Listener.
    public void actionPerformed(ActionEvent e) {
        button.setEnabled(false);
        message.setVisible(false);
        String username = usernameField.getText();
        String password = ipField.getText();
        //System.out.println("Attempting Login");
        boolean correct = true;
        String ip = fallbackPlayerIP;
        String message = "<html><font color=\"#FF0000\">Invalid Username/Password. <br>Please Try Again.</font></html>";
        if (username == null || username.trim().length() == 0) {
            correct = false;
            message = "<html><font color=\"#FF0000\">Please enter a username.</font></html>";
        } else if (remoteAuth) {
            Object[] response = new Object[]{};
            try {
                Object[] params = {username, password, clientDate};
                response = (Object[]) XmlRpcProxy.execute(loginRpcURL, "login", params);
                if (response != null && response.length > 0 && response[0] instanceof Boolean) {
                    correct = ((Boolean) response[0]).booleanValue();
                    if (response.length > 1 && response[1] instanceof String && ((String) response[1]).trim().length() > 0) {
                        ip = (String) response[1];
                    }
                    if (response.length > 4 && response[4] instanceof String) {
                        message = (String) response[4];
                    }
                }
            } catch (Exception ex) {
                correct = true;
                message = "<html><font color=\"#FF6600\">Remote login failed, using local mode.</font></html>";
            }
        }
        if (correct) {
            System.out.println("Login Successful");
            MyView.loginToServer(username, password, ip);
        } else {
            button.setEnabled(true);
            setMessage(message);
        }
        //MyView.loginToServer(username,password,ip);
    }

    public void setMessage(String message) {
        this.message.setText(message);
        this.message.setVisible(true);
        if (button != null) {
            button.setEnabled(true);
        }
        if (panel != null) {
            panel.setVisible(true);
        }
    }

    /**
     Exit program tells me that the panel containing the Hack Wars program has been closed.
     */
    public void exitProgram() {
        if (MyView != null)
            MyView.clean();
        reconnect();
        panel.setVisible(true);
        button.setEnabled(true);
    }

    public void loginFailed() {
        panel.setVisible(true);
        message.setVisible(true);
        if (button != null) {
            button.setEnabled(true);
        }
    }

    /**
     Finished loading tells me it's finished loading.
     */
    public void finishedLoading() {
        if (panel != null) {
            panel.setVisible(false);
        }
    }

    /**
     Connect to the Hack Wars Server.
     */
    public void reconnect() {
        MyView = new View(ip, this);
    }

    //Testing main.
    public static void main(String args[]) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
        }
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                final Launcher launcher = new Launcher();
                final JFrame frame = new JFrame("HackWars");
                frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                frame.setResizable(false);
                frame.setContentPane(launcher);
                frame.addWindowListener(new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        launcher.stop();
                    }
                });
                launcher.init();
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }
}
