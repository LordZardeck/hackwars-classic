package com.hackwars.integration.ui;

import assignments.RemoteFunctionCall;
import com.hackwars.gui.OSMenuBar;
import com.hackwars.integration.IntegrationFixture;
import com.hackwars.integration.IntegrationTestEnvironment;
import com.hackwars.integration.SeedScenario;
import com.hackwars.integration.expected.ExpectedFailure;
import com.hackwars.integration.expected.ExpectedFailureRule;
import gui.AttackPane;
import gui.BountyWindow;
import gui.CommandPrompt;
import gui.Deposit;
import gui.Equipment;
import gui.FTP;
import gui.FirewallBrowser;
import gui.HacktendoCreator;
import gui.Help;
import gui.Home;
import gui.LogWindow;
import gui.MapPanel;
import gui.OptionPanel;
import gui.PersonalSettings;
import gui.PortManagement;
import gui.PortScan;
import gui.ScriptEditor;
import gui.Transfer;
import gui.TutorialWindow;
import gui.WatchManager;
import gui.WebBrowser;
import gui.WebsiteEditor;
import gui.Withdraw;
import gui.ZombieAttackDialog;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JInternalFrame;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ClientWindowWorkflowTest {
    @Rule
    public final ExpectedFailureRule expectedFailureRule = new ExpectedFailureRule();

    private ClientAppDriver driver;

    @After
    public void tearDown() {
        if (driver != null) {
            driver.close();
        }
    }

    @Test
    public void menuActions_openEveryTopLevelClientWindowFamily() {
        SeedScenario scenario = IntegrationTestEnvironment.shared().scenario();
        driver = ClientAppDriver.launchOffline(scenario);

        driver.openMenu(OSMenuBar.Command.HOME);
        assertNotNull(driver.awaitFrame(Home.class));

        driver.openMenu(OSMenuBar.Command.SCRIPT_EDITOR);
        assertNotNull(driver.awaitFrame(ScriptEditor.class));

        driver.openMenu(OSMenuBar.Command.SITE_EDITOR);
        assertNotNull(driver.awaitFrame(WebsiteEditor.class));

        driver.openMenu(OSMenuBar.Command.PORT_MANAGEMENT);
        assertNotNull(driver.awaitFrame(PortManagement.class));

        driver.openMenu(OSMenuBar.Command.WATCH_MANAGER);
        assertNotNull(driver.awaitFrame(WatchManager.class));

        driver.openMenu(OSMenuBar.Command.EQUIPMENT_MANAGER);
        assertNotNull(driver.awaitFrame(Equipment.class));

        driver.openMenu(OSMenuBar.Command.FIREWALL_MANAGER);
        assertNotNull(driver.awaitFrame(FirewallBrowser.class));

        driver.openMenu(OSMenuBar.Command.PORT_SCAN);
        assertNotNull(driver.awaitFrame(PortScan.class));

        driver.openMenu(OSMenuBar.Command.ATTACK_PORT);
        assertNotNull(driver.awaitFrame(AttackPane.class));

        driver.openMenu(OSMenuBar.Command.REDIRECT_PORT);
        assertNotNull(driver.awaitFrameTitle("Redirect"));

        driver.openMenu(OSMenuBar.Command.WEB_BROWSER);
        assertNotNull(driver.awaitFrame(WebBrowser.class));

        driver.openMenu(OSMenuBar.Command.STORE);
        assertNotNull(driver.awaitFrame(WebBrowser.class));

        driver.openMenu(OSMenuBar.Command.SHOP_FTP);
        assertNotNull(driver.awaitFrame(FTP.class));

        driver.openMenu(OSMenuBar.Command.PUBLIC_FTP);
        assertNotNull(driver.awaitFrame(FTP.class));

        driver.openMenu(OSMenuBar.Command.NETWORK);
        assertNotNull(driver.awaitFrame(MapPanel.class));

        driver.openMenu(OSMenuBar.Command.LOG_WINDOW);
        assertNotNull(driver.awaitFrame(LogWindow.class));

        driver.openMenu(OSMenuBar.Command.PERSONAL_SETTINGS);
        assertNotNull(driver.awaitFrame(PersonalSettings.class));

        driver.openMenu(OSMenuBar.Command.PREFERENCES);
        assertNotNull(driver.awaitFrame(OptionPanel.class));

        driver.openMenu(OSMenuBar.Command.HACKTENDO_GAME_CREATOR);
        assertNotNull(driver.awaitFrame(HacktendoCreator.class));

        driver.openAction("Help");
        assertNotNull(driver.awaitFrame(Help.class));

        driver.openAction("Command Prompt");
        assertNotNull(driver.awaitFrame(CommandPrompt.class));

        driver.openMenu(OSMenuBar.Command.TUTORIAL_FIRST_ATTACK);
        assertNotNull(driver.awaitFrame(TutorialWindow.class));

        driver.openActionAsync(OSMenuBar.Command.ZOMBIE_ATTACK.getValue());
        JDialog zombieDialog = driver.awaitDialog("Start Zombie Attack");
        assertNotNull(zombieDialog);
        zombieDialog.dispose();

        driver.openActionAsync(OSMenuBar.Command.CREATE_BOUNTY.getValue());
        JDialog bountyDialog = driver.awaitDialog("Create Bounty");
        assertNotNull(bountyDialog);
        bountyDialog.dispose();
    }

    @Test
    public void bankingWindows_loadSeededPortsAndEmitExpectedRemoteCalls() {
        SeedScenario scenario = IntegrationTestEnvironment.shared().scenario();
        driver = ClientAppDriver.launchOffline(scenario);

        driver.gameState().clearRemoteCalls();
        driver.openMenu(OSMenuBar.Command.DEPOSIT);
        Deposit deposit = driver.awaitFrame(Deposit.class);
        JComboBox depositPorts = driver.firstComboBox(deposit);
        assertEquals("4: Integration Bank", String.valueOf(depositPorts.getItemAt(0)));
        driver.setFormattedValue(deposit, Integer.valueOf(25));
        driver.clickButton(deposit, "Deposit");
        assertEquals("deposit", driver.gameState().lastRemoteCall().getFunction());

        driver.openMenu(OSMenuBar.Command.WITHDRAW);
        Withdraw withdraw = driver.awaitFrame(Withdraw.class);
        JComboBox withdrawPorts = driver.firstComboBox(withdraw);
        assertEquals("4: Integration Bank", String.valueOf(withdrawPorts.getItemAt(0)));
        driver.setFormattedValue(withdraw, Integer.valueOf(10));
        driver.clickButton(withdraw, "Withdraw");
        assertEquals("withdraw", driver.gameState().lastRemoteCall().getFunction());

        driver.openMenu(OSMenuBar.Command.TRANSFER);
        Transfer transfer = driver.awaitFrame(Transfer.class);
        driver.setFormattedValue(transfer, Integer.valueOf(15));
        driver.setIp(transfer, "123.123.1.123");
        driver.clickButton(transfer, "Transfer");
        RemoteFunctionCall transferCall = driver.gameState().lastRemoteCall();
        assertEquals("transfer", transferCall.getFunction());
        Object[] parameters = (Object[]) transferCall.getParameters();
        assertEquals("LOCAL-IP", parameters[1]);
        assertEquals("123.123.1.123", parameters[2]);
    }

    @Test
    public void homeAndSiteEditor_receiveSeededData_andSaveWorkflowQueuesRequests() {
        SeedScenario scenario = IntegrationTestEnvironment.shared().scenario();
        driver = ClientAppDriver.launchOffline(scenario);

        driver.openMenu(OSMenuBar.Command.HOME);
        Home home = driver.awaitFrame(Home.class);
        driver.seedHomeDirectory(IntegrationFixture.homeDirectory());
        assertEquals(3, driver.hacker().getCurrentDirectory().length);

        driver.gameState().clearRemoteCalls();
        driver.openMenu(OSMenuBar.Command.SITE_EDITOR);
        WebsiteEditor editor = driver.awaitFrame(WebsiteEditor.class);
        List<RemoteFunctionCall> initialCalls = driver.gameState().getRemoteCallsSnapshot();
        assertTrue(initialCalls.stream().anyMatch(call -> "requestpage".equals(call.getFunction())));

        driver.seedSitePage(scenario.getWebsiteTitle(), scenario.getWebsiteBody());
        JEditorPane editorPane = driver.first(editor, JEditorPane.class);
        assertTrue(editorPane.getText().contains("Hello from integration web page."));
        assertTrue(editor.getTitle().contains(scenario.getWebsiteTitle()));
    }

    @Test
    @ExpectedFailure(reason = "Known bug: the web browser does not currently render received pages correctly.")
    public void webBrowserRendering_isTrackedAsExpectedFailureUntilRenderingBugIsFixed() {
        SeedScenario scenario = IntegrationTestEnvironment.shared().scenario();
        driver = ClientAppDriver.launchOffline(scenario);

        driver.openMenu(OSMenuBar.Command.WEB_BROWSER);
        WebBrowser browser = driver.awaitFrame(WebBrowser.class);
        driver.seedSitePage(scenario.getWebsiteTitle(), scenario.getWebsiteBody());

        assertTrue("Expected browser view to expose rendered page text.",
            driver.containsText(browser, "Hello from integration web page."));
    }

    @Test
    @ExpectedFailure(reason = "Known client workflow failure: Hacktendo Game Player currently requires JOGL natives that are not loading in the test runtime.")
    public void hacktendoPlayerLaunch_isTrackedAsExpectedFailureUntilNativeLoadingIsFixed() {
        SeedScenario scenario = IntegrationTestEnvironment.shared().scenario();
        driver = ClientAppDriver.launchOffline(scenario);

        driver.openAction("Hacktendo Game Player");
        assertNotNull(driver.awaitFrameTitle("Hacktendo"));
    }
}
