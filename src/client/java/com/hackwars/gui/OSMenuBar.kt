package com.hackwars.gui

import gui.ImageLoader
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem

class OSMenuBar : JMenuBar() {
    class ApplicationMenu : JMenu("Applications") {
        class BankingMenu : JMenu("Banking") {
            class DepositMenuItem : JMenuItem("Deposit", ImageLoader.getImageIcon("images/calc.png"))
            class WithdrawMenuItem : JMenuItem("Withdraw", ImageLoader.getImageIcon("images/calc.png"))
            class TransferMenuItem : JMenuItem("Transfer", ImageLoader.getImageIcon("images/calc.png"))

            val depositMenuItem = add(DepositMenuItem()) as DepositMenuItem
            val withdrawMenuItem = add(WithdrawMenuItem()) as WithdrawMenuItem
            val transferMenuItem = add(TransferMenuItem()) as TransferMenuItem
        }

        class InternetMenu : JMenu("Internet") {
            class WebBrowserMenuItem : JMenuItem("Web Browser", ImageLoader.getImageIcon("images/browser.png"))
            class StoreMenuItem : JMenuItem("Store", ImageLoader.getImageIcon("images/browser.png"))
            class SiteEditorMenuItem : JMenuItem("Site Editor", ImageLoader.getImageIcon("images/edit.png"))

            val webBrowserMenuItem = add(WebBrowserMenuItem()) as WebBrowserMenuItem
            val storeMenuItem = add(StoreMenuItem()) as StoreMenuItem
            val siteEditorMenuItem = add(SiteEditorMenuItem()) as SiteEditorMenuItem
        }

        class HackingToolsMenu : JMenu("Hacking Tools") {
            class PortScanMenuItem : JMenuItem("Port Scan", ImageLoader.getImageIcon("images/scan.png"))
            class AttackPortMenuItem : JMenuItem("Attack Port", ImageLoader.getImageIcon("images/attack.png"))
            class RedirectPortMenuItem : JMenuItem("Redirect Port", ImageLoader.getImageIcon("images/redirect.png"))
            class ZombieAttackMenuItem : JMenuItem("Zombie Attack", ImageLoader.getImageIcon("images/attack.png"))

            val portScanMenuItem = add(PortScanMenuItem()) as PortScanMenuItem
            val attackPortMenuItem = add(AttackPortMenuItem()) as AttackPortMenuItem
            val redirectPortMenuItem = add(RedirectPortMenuItem()) as RedirectPortMenuItem
            val zombieAttackMenuItem = add(ZombieAttackMenuItem()) as ZombieAttackMenuItem
        }

        class ScriptEditorMenuItem : JMenuItem("Script Editor", ImageLoader.getImageIcon("images/edit.png"))
        class CreateBountyMenuItem : JMenuItem("Create Bounty")
        class HacktendoGameCreatorMenuItem : JMenuItem("Hacktendo Game Creator")

        val bankingMenu = add(BankingMenu()) as BankingMenu
        val internetMenu = add(InternetMenu()) as InternetMenu
        val hackingToolsMenu = add(HackingToolsMenu()) as HackingToolsMenu
        val scriptEditorMenuItem = add(ScriptEditorMenuItem()) as ScriptEditorMenuItem
        val createBountyMenuItem = add(CreateBountyMenuItem()) as CreateBountyMenuItem
        var hacktendoGameCreatorMenuItem: HacktendoGameCreatorMenuItem? = null
            private set

        var hacktendoPurchased = true
            set(value) {
                field = value
                if (value) hacktendoGameCreatorMenuItem = add(HacktendoGameCreatorMenuItem()) as HacktendoGameCreatorMenuItem
                else hacktendoGameCreatorMenuItem?.let { remove(it) }
            }
    }

    val applicationMenu = add(ApplicationMenu()) as ApplicationMenu
}