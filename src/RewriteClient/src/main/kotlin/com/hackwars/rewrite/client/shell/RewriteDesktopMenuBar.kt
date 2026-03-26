package com.hackwars.rewrite.client.shell

import java.awt.Component
import java.awt.Dimension
import java.util.EnumMap
import javax.swing.Box
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem

class RewriteDesktopMenuBar : JMenuBar() {
    val taskBar = RewriteDesktopTaskBar()
    private val commandItems = EnumMap<RewriteShellCommand, JMenuItem>(RewriteShellCommand::class.java)

    init {
        name = "rewrite-shell-menu-bar"
        add(buildApplicationsMenu())
        add(buildPlacesMenu())
        add(buildSystemMenu())
        add(buildTutorialsMenu())
        add(Box.createHorizontalGlue())
        add(taskBar)
        preferredSize = Dimension(preferredSize.width, 30)
    }

    fun topLevelMenus(): List<JMenu> {
        return components.filterIsInstance<JMenu>()
    }

    fun commandItem(command: RewriteShellCommand): JMenuItem? = commandItems[command]

    fun commandItemsByCommand(): Map<RewriteShellCommand, JMenuItem> = commandItems.toMap()

    private fun buildApplicationsMenu(): JMenu {
        return JMenu("Applications").apply {
            name = "rewrite-shell-menu-applications"
            add(
                JMenu("Banking").apply {
                    name = "rewrite-shell-menu-applications-banking"
                    add(buildCommandItem(RewriteShellCommand.DEPOSIT))
                    add(buildCommandItem(RewriteShellCommand.WITHDRAW))
                    add(buildCommandItem(RewriteShellCommand.TRANSFER))
                },
            )
            add(
                JMenu("Internet").apply {
                    name = "rewrite-shell-menu-applications-internet"
                    add(buildCommandItem(RewriteShellCommand.WEB_BROWSER))
                    add(buildCommandItem(RewriteShellCommand.STORE))
                    add(buildCommandItem(RewriteShellCommand.SITE_EDITOR))
                },
            )
            add(
                JMenu("Hacking Tools").apply {
                    name = "rewrite-shell-menu-applications-hacking-tools"
                    add(buildCommandItem(RewriteShellCommand.PORT_SCAN))
                    add(buildCommandItem(RewriteShellCommand.ATTACK_PORT))
                    add(buildCommandItem(RewriteShellCommand.REDIRECT_PORT))
                    add(buildCommandItem(RewriteShellCommand.ZOMBIE_ATTACK))
                },
            )
            add(buildCommandItem(RewriteShellCommand.SCRIPT_EDITOR))
            add(buildCommandItem(RewriteShellCommand.CREATE_BOUNTY))
        }
    }

    private fun buildPlacesMenu(): JMenu {
        return JMenu("Places").apply {
            name = "rewrite-shell-menu-places"
            add(buildCommandItem(RewriteShellCommand.SHOP_FTP))
            add(buildCommandItem(RewriteShellCommand.PUBLIC_FTP))
            add(buildCommandItem(RewriteShellCommand.HOME))
            add(buildCommandItem(RewriteShellCommand.NETWORK))
            add(buildCommandItem(RewriteShellCommand.LOG_WINDOW))
        }
    }

    private fun buildSystemMenu(): JMenu {
        return JMenu("System").apply {
            name = "rewrite-shell-menu-system"
            add(buildCommandItem(RewriteShellCommand.PORT_MANAGEMENT))
            add(buildCommandItem(RewriteShellCommand.WATCH_MANAGER))
            add(buildCommandItem(RewriteShellCommand.EQUIPMENT_MANAGER))
            add(buildCommandItem(RewriteShellCommand.FIREWALL_MANAGER))
            add(buildCommandItem(RewriteShellCommand.SET_PUBLIC_FTP_PASSWORD))
            add(buildCommandItem(RewriteShellCommand.PERSONAL_SETTINGS))
            add(buildCommandItem(RewriteShellCommand.PREFERENCES))
        }
    }

    private fun buildTutorialsMenu(): JMenu {
        return JMenu("Tutorials").apply {
            name = "rewrite-shell-menu-tutorials"
            add(buildCommandItem(RewriteShellCommand.TUTORIAL_FIRST_ATTACK))
        }
    }

    private fun buildCommandItem(command: RewriteShellCommand): JMenuItem {
        return JMenuItem(command.title).apply {
            name = "rewrite-shell-command-${command.stableId}"
            alignmentX = Component.LEFT_ALIGNMENT
            commandItems[command] = this
        }
    }
}
