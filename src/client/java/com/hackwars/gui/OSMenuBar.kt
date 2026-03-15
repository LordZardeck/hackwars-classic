package com.hackwars.gui

import java.awt.Color
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.Icon
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem

fun LEGACY_getImageIcon(iconPath: String?): Icon? {
    if (iconPath == null) {
        return null
    }
    return try {

        val imageLoaderClass = Class.forName("gui.ImageLoader")
        val getImageIcon = imageLoaderClass.getMethod("getImageIcon", String::class.java)
        getImageIcon.invoke(null, iconPath) as? Icon
    } catch (_: Throwable) {
        null
    }
}

class OSMenuBar : JMenuBar(), ActionListener {
    enum class Command(val value: String) {
        DEPOSIT("Deposit"),
        WITHDRAW("Withdraw"),
        TRANSFER("Transfer"),
        WEB_BROWSER("Web Browser"),
        STORE("Store"),
        SITE_EDITOR("Site Editor"),
        PORT_SCAN("Port Scan"),
        ATTACK_PORT("Attack Port"),
        REDIRECT_PORT("Redirect Port"),
        ZOMBIE_ATTACK("Zombie Attack"),
        SCRIPT_EDITOR("Script Editor"),
        CREATE_BOUNTY("Create Bounty"),
        HACKTENDO_GAME_CREATOR("Hacktendo Game Creator"),
        SHOP_FTP("Shop FTP"),
        PUBLIC_FTP("Public FTP"),
        HOME("Home"),
        NETWORK("Network"),
        LOG_WINDOW("Log Window"),
        PORT_MANAGEMENT("Port Management"),
        WATCH_MANAGER("Watch Manager"),
        EQUIPMENT_MANAGER("Equipment Manager"),
        FIREWALL_MANAGER("Firewall Manager"),
        SET_PUBLIC_FTP_PASSWORD("FTPPass"),
        PERSONAL_SETTINGS("Personal Settings"),
        PREFERENCES("preferences"),
        TUTORIAL_FIRST_ATTACK("First Attack");

        fun matches(command: String): Boolean {
            return value == command
        }
    }

    inner class OSMenuBarItem(text: String, command: Command, iconPath: String? = null) :
        JMenuItem(text, LEGACY_getImageIcon(iconPath)) {
        init {
            addActionListener(this@OSMenuBar)
            actionCommand = command.value
        }
    }

    private val applicationMenu = object : JMenu("Applications") {
        private val bankingMenu = object : JMenu("Banking") {
            val depositMenuItem = add(OSMenuBarItem("Deposit", Command.DEPOSIT, "images/calc.png"))
            val withdrawMenuItem = add(OSMenuBarItem("Withdraw", Command.WITHDRAW, "images/calc.png"))
            val transferMenuItem = add(OSMenuBarItem("Transfer", Command.TRANSFER, "images/calc.png"))
        }.also { add(it) }

        private val internetMenu = object : JMenu("Internet") {
            val webBrowserMenuItem = add(OSMenuBarItem("Web Browser", Command.WEB_BROWSER, "images/browser.png"))
            val storeMenuItem = add(OSMenuBarItem("Store", Command.STORE, "images/browser.png"))
            val siteEditorMenuItem = add(OSMenuBarItem("Site Editor", Command.SITE_EDITOR, "images/edit.png"))
        }.also { add(it) }

        private val hackingToolsMenu = object : JMenu("Hacking Tools") {
            val portScanMenuItem = add(OSMenuBarItem("Port Scan", Command.PORT_SCAN, "images/scan.png"))
            val attackPortMenuItem = add(OSMenuBarItem("Attack Port", Command.ATTACK_PORT, "images/attack.png"))
            val redirectPortMenuItem = add(OSMenuBarItem("Redirect Port", Command.REDIRECT_PORT, "images/redirect.png"))
            val zombieAttackMenuItem = add(OSMenuBarItem("Zombie Attack", Command.ZOMBIE_ATTACK, "images/attack.png"))
        }.also { add(it) }

        private val scriptEditorMenuItem = add(OSMenuBarItem("Script Editor", Command.SCRIPT_EDITOR, "images/edit.png"))
        private val createBountyMenuItem = add(OSMenuBarItem("Create Bounty", Command.CREATE_BOUNTY))
        private var hacktendoGameCreatorMenuItem: JMenuItem? = null

        var hacktendoPurchased = true
            set(value) {
                field = value
                if (value) hacktendoGameCreatorMenuItem =
                    add(OSMenuBarItem("Hacktendo Game Creator", Command.HACKTENDO_GAME_CREATOR))
                else hacktendoGameCreatorMenuItem?.let { remove(it) }
            }
    }.also { add(it) }

    private val placesMenu = object : JMenu("Places") {
        private val shopFtpMenuItem = add(OSMenuBarItem("Shop FTP", Command.SHOP_FTP, "images/ftp.png"))
        private val publicFtpMenuItem = add(OSMenuBarItem("Public FTP", Command.PUBLIC_FTP, "images/ftp.png"))
        private val homeMenuItem = add(OSMenuBarItem("Home", Command.HOME, "images/home.png"))
        private val networkMenuItem = add(OSMenuBarItem("Network", Command.NETWORK, "images/home.png"))
        private val logWindowMenuItem = add(OSMenuBarItem("Log Window", Command.LOG_WINDOW))
    }.also { add(it) }

    private val systemMenu = object : JMenu("System") {
        val portManagementMenuItem = add(OSMenuBarItem("Port Management", Command.PORT_MANAGEMENT, "images/ports.png"))
        val watchManagerMenuItem = add(OSMenuBarItem("Watch Manager", Command.WATCH_MANAGER, "images/watch.png"))
        val equipmentManagerMenuItem =
            add(OSMenuBarItem("Equipment Manager", Command.EQUIPMENT_MANAGER, "images/cpu.png"))
        val firewallManagerMenuItem =
            add(OSMenuBarItem("Firewall Manager", Command.FIREWALL_MANAGER, "images/firewall.png"))
        val setPublicFtpPasswordMenuItem =
            add(OSMenuBarItem("Set Public FTP Password", Command.SET_PUBLIC_FTP_PASSWORD))
        val personalSettingsMenuItem = add(OSMenuBarItem("Personal Settings", Command.PERSONAL_SETTINGS))
        val preferencesMenuItem = add(OSMenuBarItem("Preferences...", Command.PREFERENCES))
    }.also { add(it) }

    private val tutorialMenu = object : JMenu("Tutorials") {
        val firstAttackMenuItem = add(OSMenuBarItem("First Attack", Command.TUTORIAL_FIRST_ATTACK, "images/attack.png"))
    }.also { add(it) }

    val taskBar = TaskBar().also {
        add(it)
    }

    init {
        preferredSize = Dimension(preferredSize.width, 30)
    }

    fun addActionListener(l: ActionListener) {
        listenerList.add(ActionListener::class.java, l)
    }
    override fun actionPerformed(e: ActionEvent?) {
        // Guaranteed to return a non-null array
        val listeners = listenerList.getListenerList()

        // Process the listeners last to first, notifying
        // those that are interested in this event
        var i = listeners.size - 2
        while (i >= 0) {
            if (listeners[i] === ActionListener::class.java) {
                (listeners[i + 1] as ActionListener).actionPerformed(e)
            }
            i -= 2
        }
    }
}
