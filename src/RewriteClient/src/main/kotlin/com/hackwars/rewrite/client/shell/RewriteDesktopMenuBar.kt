package com.hackwars.rewrite.client.shell

import java.awt.Component
import java.awt.Dimension
import javax.swing.Box
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem

class RewriteDesktopMenuBar(
    private val onCommandSelected: (RewriteShellCommand) -> Unit,
) : JMenuBar() {
    val taskBar = RewriteDesktopTaskBar()

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

    private fun buildApplicationsMenu(): JMenu {
        return JMenu("Applications").apply {
            name = "rewrite-shell-menu-applications"
            add(
                JMenu("Banking").apply {
                    name = "rewrite-shell-menu-applications-banking"
                    add(commandItem(RewriteShellCommand.DEPOSIT))
                    add(commandItem(RewriteShellCommand.WITHDRAW))
                    add(commandItem(RewriteShellCommand.TRANSFER))
                },
            )
            add(
                JMenu("Internet").apply {
                    name = "rewrite-shell-menu-applications-internet"
                    add(commandItem(RewriteShellCommand.WEB_BROWSER))
                    add(commandItem(RewriteShellCommand.STORE))
                    add(commandItem(RewriteShellCommand.SITE_EDITOR))
                },
            )
            add(
                JMenu("Hacking Tools").apply {
                    name = "rewrite-shell-menu-applications-hacking-tools"
                    add(commandItem(RewriteShellCommand.PORT_SCAN))
                    add(commandItem(RewriteShellCommand.ATTACK_PORT))
                    add(commandItem(RewriteShellCommand.REDIRECT_PORT))
                    add(commandItem(RewriteShellCommand.ZOMBIE_ATTACK))
                },
            )
            add(commandItem(RewriteShellCommand.SCRIPT_EDITOR))
            add(commandItem(RewriteShellCommand.CREATE_BOUNTY))
        }
    }

    private fun buildPlacesMenu(): JMenu {
        return JMenu("Places").apply {
            name = "rewrite-shell-menu-places"
            add(commandItem(RewriteShellCommand.SHOP_FTP))
            add(commandItem(RewriteShellCommand.PUBLIC_FTP))
            add(commandItem(RewriteShellCommand.HOME))
            add(commandItem(RewriteShellCommand.NETWORK))
            add(commandItem(RewriteShellCommand.LOG_WINDOW))
        }
    }

    private fun buildSystemMenu(): JMenu {
        return JMenu("System").apply {
            name = "rewrite-shell-menu-system"
            add(commandItem(RewriteShellCommand.PORT_MANAGEMENT))
            add(commandItem(RewriteShellCommand.WATCH_MANAGER))
            add(commandItem(RewriteShellCommand.EQUIPMENT_MANAGER))
            add(commandItem(RewriteShellCommand.FIREWALL_MANAGER))
            add(commandItem(RewriteShellCommand.SET_PUBLIC_FTP_PASSWORD))
            add(commandItem(RewriteShellCommand.PERSONAL_SETTINGS))
            add(commandItem(RewriteShellCommand.PREFERENCES))
        }
    }

    private fun buildTutorialsMenu(): JMenu {
        return JMenu("Tutorials").apply {
            name = "rewrite-shell-menu-tutorials"
            add(commandItem(RewriteShellCommand.TUTORIAL_FIRST_ATTACK))
        }
    }

    private fun commandItem(command: RewriteShellCommand): JMenuItem {
        return JMenuItem(command.title).apply {
            name = "rewrite-shell-command-${command.stableId}"
            alignmentX = Component.LEFT_ALIGNMENT
            addActionListener { onCommandSelected(command) }
        }
    }
}
