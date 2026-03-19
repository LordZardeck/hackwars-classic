package com.hackwars.integration.ui;

import assignments.PacketNetwork;
import assignments.PacketPort;
import com.hackwars.client.ClientUiBootstrap;
import com.hackwars.gui.OSMenuBar;
import com.hackwars.integration.IntegrationFixture;
import com.hackwars.integration.RecordingGameState;
import com.hackwars.integration.SeedScenario;
import gui.Hacker;
import gui.IPPanel;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JInternalFrame;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.time.Duration;
import java.util.List;

public final class ClientAppDriver implements AutoCloseable {
    private final RecordingGameState gameState;
    private final Hacker hacker;

    private ClientAppDriver(RecordingGameState gameState, Hacker hacker) {
        this.gameState = gameState;
        this.hacker = hacker;
    }

    public static ClientAppDriver launchOffline(SeedScenario scenario) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new AssertionError("clientUiTest requires a graphics environment.");
        }
        ClientUiBootstrap.installLookAndFeel();
        RecordingGameState gameState = new RecordingGameState();
        Hacker hacker = SwingTestSupport.callOnEdt(() -> new Hacker(
            gameState,
            scenario.getPlayerName(),
            scenario.getPlayerIp(),
            false,
            scenario.getOfflineEncryptedIp(),
            true
        ));
        ClientAppDriver driver = new ClientAppDriver(gameState, hacker);
        driver.seedPorts(IntegrationFixture.defaultPorts());
        driver.seedEconomy(250.0f, 500.0f);
        driver.seedNetwork(IntegrationFixture.network("100.10.1.99"));
        return driver;
    }

    public RecordingGameState gameState() {
        return gameState;
    }

    public Hacker hacker() {
        return hacker;
    }

    public void seedPorts(PacketPort[] ports) {
        SwingTestSupport.runOnEdt(() -> hacker.setPorts(ports));
    }

    public void seedEconomy(float pettyCash, float bankMoney) {
        SwingTestSupport.runOnEdt(() -> {
            hacker.setPettyCash(pettyCash);
            hacker.setBankMoney(bankMoney);
            hacker.setDefaultBank(4);
            hacker.setDefaultAttack(3);
            hacker.setDefaultRedirect(9);
            hacker.setDefaultFTP(6);
            hacker.setDefaultHTTP(8);
        });
    }

    public void seedNetwork(PacketNetwork network) {
        SwingTestSupport.runOnEdt(() -> hacker.setNetwork(network));
    }

    public void seedHomeDirectory(Object[] directory) {
        SwingTestSupport.runOnEdt(() -> hacker.receivedDirectory(directory));
    }

    public void seedSitePage(String title, String body) {
        SwingTestSupport.runOnEdt(() -> hacker.receivedPage(title, body));
    }

    public void seedStoreDirectory(Object[] directory) {
        SwingTestSupport.runOnEdt(() -> hacker.receivedDirectory(directory));
    }

    public void openMenu(OSMenuBar.Command command) {
        openAction(command.getValue());
    }

    public void openAction(String actionCommand) {
        SwingTestSupport.runOnEdt(() -> hacker.actionPerformed(
            new ActionEvent(hacker, ActionEvent.ACTION_PERFORMED, actionCommand)
        ));
    }

    public void openActionAsync(String actionCommand) {
        Thread thread = new Thread(() -> hacker.actionPerformed(
            new ActionEvent(hacker, ActionEvent.ACTION_PERFORMED, actionCommand)
        ), "ClientAppDriver-" + actionCommand.replace(' ', '-'));
        thread.setDaemon(true);
        thread.start();
    }

    public <T extends JInternalFrame> T awaitFrame(Class<T> type) {
        return SwingTestSupport.waitFor(
            type.getSimpleName(),
            Duration.ofSeconds(3),
            () -> SwingTestSupport.callOnEdt(() -> {
                for (JInternalFrame frame : hacker.getPanel().getAllFrames()) {
                    if (type.isInstance(frame) && frame.isVisible()) {
                        return type.cast(frame);
                    }
                }
                return null;
            }),
            frame -> frame != null
        );
    }

    public JInternalFrame awaitFrameTitle(String titleFragment) {
        return SwingTestSupport.waitFor(
            titleFragment,
            Duration.ofSeconds(3),
            () -> SwingTestSupport.callOnEdt(() -> {
                for (JInternalFrame frame : hacker.getPanel().getAllFrames()) {
                    if (frame.isVisible() && frame.getTitle() != null && frame.getTitle().contains(titleFragment)) {
                        return frame;
                    }
                }
                return null;
            }),
            frame -> frame != null
        );
    }

    public JDialog awaitDialog(String titleFragment) {
        return SwingTestSupport.waitFor(
            titleFragment,
            Duration.ofSeconds(3),
            () -> SwingTestSupport.callOnEdt(() -> {
                for (Window window : Window.getWindows()) {
                    if (window instanceof JDialog && window.isShowing()) {
                        JDialog dialog = (JDialog) window;
                        if (dialog.getTitle() != null && dialog.getTitle().contains(titleFragment)) {
                            return dialog;
                        }
                    }
                }
                return null;
            }),
            dialog -> dialog != null
        );
    }

    public void clickButton(Container root, String text) {
        AbstractButton button = SwingTestSupport.callOnEdt(() -> {
            for (AbstractButton candidate : SwingTestSupport.findComponents(root, AbstractButton.class)) {
                if (text.equals(candidate.getText())) {
                    return candidate;
                }
            }
            return null;
        });
        if (button == null) {
            throw new AssertionError("Could not find button with text '" + text + "'");
        }
        SwingTestSupport.runOnEdt(button::doClick);
    }

    public void setFormattedValue(Container root, Number number) {
        JFormattedTextField field = first(root, JFormattedTextField.class);
        SwingTestSupport.runOnEdt(() -> {
            field.setValue(number);
            field.setText(String.valueOf(number));
        });
    }

    public void setIp(Container root, String ip) {
        IPPanel panel = first(root, IPPanel.class);
        SwingTestSupport.runOnEdt(() -> panel.setIP(ip));
    }

    public JComboBox firstComboBox(Container root) {
        return first(root, JComboBox.class);
    }

    public <T extends Component> T first(Container root, Class<T> type) {
        T component = SwingTestSupport.callOnEdt(() -> {
            List<T> matches = SwingTestSupport.findComponents(root, type);
            return matches.isEmpty() ? null : matches.get(0);
        });
        if (component == null) {
            throw new AssertionError("Could not find component of type " + type.getSimpleName());
        }
        return component;
    }

    public boolean containsText(Container root, String expectedText) {
        Boolean result = SwingTestSupport.callOnEdt(() -> {
            for (JTextComponent textComponent : SwingTestSupport.findComponents(root, JTextComponent.class)) {
                if (textComponent.getText() != null && textComponent.getText().contains(expectedText)) {
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        });
        return result.booleanValue();
    }

    @Override
    public void close() {
        SwingTestSupport.runOnEdt(() -> {
            hacker.shutdownForTests();
            Frame frame = hacker.getFrame();
            if (frame != null) {
                frame.dispose();
            }
        });
        SwingTestSupport.disposeAllWindows();
    }
}
