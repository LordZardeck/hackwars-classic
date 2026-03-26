package com.hackwars.rewrite.client.shell

enum class RewriteShellCommand(
    val title: String,
    val menuPath: List<String>,
) {
    DEPOSIT("Deposit", listOf("Applications", "Banking")),
    WITHDRAW("Withdraw", listOf("Applications", "Banking")),
    TRANSFER("Transfer", listOf("Applications", "Banking")),
    WEB_BROWSER("Web Browser", listOf("Applications", "Internet")),
    STORE("Store", listOf("Applications", "Internet")),
    SITE_EDITOR("Site Editor", listOf("Applications", "Internet")),
    PORT_SCAN("Port Scan", listOf("Applications", "Hacking Tools")),
    ATTACK_PORT("Attack Port", listOf("Applications", "Hacking Tools")),
    REDIRECT_PORT("Redirect Port", listOf("Applications", "Hacking Tools")),
    ZOMBIE_ATTACK("Zombie Attack", listOf("Applications", "Hacking Tools")),
    SCRIPT_EDITOR("Script Editor", listOf("Applications")),
    CREATE_BOUNTY("Create Bounty", listOf("Applications")),
    HACKTENDO_GAME_CREATOR("Hacktendo Game Creator", listOf("Applications")),
    SHOP_FTP("Shop FTP", listOf("Places")),
    PUBLIC_FTP("Public FTP", listOf("Places")),
    HOME("Home", listOf("Places")),
    NETWORK("Network", listOf("Places")),
    LOG_WINDOW("Log Window", listOf("Places")),
    PORT_MANAGEMENT("Port Management", listOf("System")),
    WATCH_MANAGER("Watch Manager", listOf("System")),
    EQUIPMENT_MANAGER("Equipment Manager", listOf("System")),
    FIREWALL_MANAGER("Firewall Manager", listOf("System")),
    SET_PUBLIC_FTP_PASSWORD("Set Public FTP Password", listOf("System")),
    PERSONAL_SETTINGS("Personal Settings", listOf("System")),
    PREFERENCES("Preferences...", listOf("System")),
    TUTORIAL_FIRST_ATTACK("First Attack", listOf("Tutorials"));

    val stableId: String = name.lowercase()
}
