package com.hackwars.rewrite.gamecore

import kotlinx.serialization.Serializable

const val RETAINED_FIRST_ATTACK_TUTORIAL_ID: String = "first-attack"

@Serializable
data class RequestHelpTopicListPayload(
    val topicGroup: String? = null,
)

@Serializable
data class RequestTutorialPayload(
    val tutorialId: String? = null,
)

@Serializable
data class HelpTopicEntry(
    val name: String = "",
    val id: String = "",
    val targetUrl: String = "",
)

@Serializable
data class HelpTopicListResponse(
    val topicGroup: String = "",
    val topics: List<HelpTopicEntry> = emptyList(),
)

@Serializable
data class TutorialResponse(
    val tutorialId: String = "",
    val title: String = "",
    val body: String = "",
)

data class RetainedWebsitePage(
    val targetStateId: GameStateId,
    val title: String,
    val body: String,
)

interface RetainedHelpTutorialRepository {
    suspend fun loadHelpTopics(topicGroup: String): List<HelpTopicEntry>

    suspend fun loadTutorial(tutorialId: String): TutorialResponse?

    suspend fun loadWebsite(targetStateId: GameStateId): RetainedWebsitePage?
}

object NoOpRetainedHelpTutorialRepository : RetainedHelpTutorialRepository {
    override suspend fun loadHelpTopics(topicGroup: String): List<HelpTopicEntry> = emptyList()

    override suspend fun loadTutorial(tutorialId: String): TutorialResponse? = null

    override suspend fun loadWebsite(targetStateId: GameStateId): RetainedWebsitePage? = null
}

class DefaultRetainedHelpTutorialRepository : RetainedHelpTutorialRepository {
    private val topicsByGroup: Map<String, List<HelpTopicEntry>> = mapOf(
        HELP_TOPIC_GROUP_TUTORIALS to listOf(
            helpTopic(
                id = RETAINED_FIRST_ATTACK_TUTORIAL_ID,
                name = "First Attack",
                targetIp = RETAINED_FIRST_ATTACK_TOPIC_IP,
            ),
        ),
        HELP_TOPIC_GROUP_BANKING to listOf(
            helpTopic("banking-deposit", "Deposit Money", RETAINED_BANKING_DEPOSIT_TOPIC_IP),
            helpTopic("banking-withdraw", "Withdraw Money", RETAINED_BANKING_WITHDRAW_TOPIC_IP),
            helpTopic("banking-transfer", "Transfer Money", RETAINED_BANKING_TRANSFER_TOPIC_IP),
        ),
        HELP_TOPIC_GROUP_ATTACK to listOf(
            helpTopic("attack-port", "Attack Port", RETAINED_ATTACK_PORT_TOPIC_IP),
            helpTopic("redirect-port", "Redirect Port", RETAINED_REDIRECT_PORT_TOPIC_IP),
            helpTopic("zombie-attack", "Zombie Attack", RETAINED_ZOMBIE_ATTACK_TOPIC_IP),
        ),
        HELP_TOPIC_GROUP_FTP to listOf(
            helpTopic("shop-ftp", "Shop FTP", RETAINED_SHOP_FTP_TOPIC_IP),
            helpTopic("public-ftp", "Public FTP", RETAINED_PUBLIC_FTP_TOPIC_IP),
        ),
        HELP_TOPIC_GROUP_WATCH to listOf(
            helpTopic("watch-manager", "Watch Manager", RETAINED_WATCH_MANAGER_TOPIC_IP),
        ),
        HELP_TOPIC_GROUP_CHALLENGE_API to listOf(
            helpTopic("bounty-files", "Bounty Files", RETAINED_BOUNTY_TOPIC_IP),
        ),
        HELP_TOPIC_GROUP_OTHER to listOf(
            helpTopic("network-switching", "Network Switching", RETAINED_NETWORK_SWITCHING_TOPIC_IP),
            helpTopic("port-management", "Port Management", RETAINED_PORT_MANAGEMENT_TOPIC_IP),
        ),
        HELP_TOPIC_GROUP_CHALLENGES to listOf(
            helpTopic("root-attacker", "Root Attacker", RETAINED_ROOT_ATTACKER_CHALLENGE_TOPIC_IP),
            helpTopic("merchant-banker", "Merchant Banker", RETAINED_MERCHANT_BANKER_CHALLENGE_TOPIC_IP),
        ),
    )

    private val tutorialsById: Map<String, TutorialResponse> = mapOf(
        RETAINED_FIRST_ATTACK_TUTORIAL_ID to TutorialResponse(
            tutorialId = RETAINED_FIRST_ATTACK_TUTORIAL_ID,
            title = "First Attack",
            body =
                """
                <p>Welcome to Hack Wars! This tutorial will help you get started.</p>
                <p>To get started, you will need some basic scripts.</p>
                <p>In order to do this, go to Applications&gt;Internet&gt;Store</p>
                """.trimIndent(),
        ),
    )

    private val websitesByStateId: Map<GameStateId, RetainedWebsitePage> = listOf(
        retainedWebsite(
            targetIp = RETAINED_FIRST_ATTACK_TOPIC_IP,
            title = "First Attack",
            body =
                """
                <html><body>
                <h1>First Attack</h1>
                <p>Visit the Store to buy a basic banking binary.</p>
                <p>Then open Port Management and install the program onto a live port.</p>
                <p>Once you have an active banking program, you can begin building petty cash for your first attack.</p>
                </body></html>
                """.trimIndent(),
        ),
        retainedWebsite(
            targetIp = RETAINED_BANKING_DEPOSIT_TOPIC_IP,
            title = "Deposit Money",
            body = helpHtml("Deposit Money", "Use a live bank port to move petty cash into your bank balance."),
        ),
        retainedWebsite(
            targetIp = RETAINED_BANKING_WITHDRAW_TOPIC_IP,
            title = "Withdraw Money",
            body = helpHtml("Withdraw Money", "Use a live bank port to move banked money back into petty cash."),
        ),
        retainedWebsite(
            targetIp = RETAINED_BANKING_TRANSFER_TOPIC_IP,
            title = "Transfer Money",
            body = helpHtml("Transfer Money", "Transfers send money through a configured bank port to another target IP."),
        ),
        retainedWebsite(
            targetIp = RETAINED_ATTACK_PORT_TOPIC_IP,
            title = "Attack Port",
            body = helpHtml("Attack Port", "Attack programs launch from enabled attack ports and run against a target IP and port."),
        ),
        retainedWebsite(
            targetIp = RETAINED_REDIRECT_PORT_TOPIC_IP,
            title = "Redirect Port",
            body = helpHtml("Redirect Port", "Redirect programs consume a redirect-capable source port and point traffic at a target."),
        ),
        retainedWebsite(
            targetIp = RETAINED_ZOMBIE_ATTACK_TOPIC_IP,
            title = "Zombie Attack",
            body = helpHtml("Zombie Attack", "Zombie attacks run from a controlled remote host while your local machine coordinates the strike."),
        ),
        retainedWebsite(
            targetIp = RETAINED_SHOP_FTP_TOPIC_IP,
            title = "Shop FTP",
            body = helpHtml("Shop FTP", "Shop FTP lets you publish local files to your store listing through an enabled FTP port."),
        ),
        retainedWebsite(
            targetIp = RETAINED_PUBLIC_FTP_TOPIC_IP,
            title = "Public FTP",
            body = helpHtml("Public FTP", "Public FTP is a read-only browser for a remote target's /Public directory."),
        ),
        retainedWebsite(
            targetIp = RETAINED_WATCH_MANAGER_TOPIC_IP,
            title = "Watch Manager",
            body = helpHtml("Watch Manager", "Watch programs observe ports, scan triggers, and petty-cash conditions from the Watch Manager."),
        ),
        retainedWebsite(
            targetIp = RETAINED_BOUNTY_TOPIC_IP,
            title = "Bounty Files",
            body = helpHtml("Bounty Files", "Bounties let you publish a target file reward into the network store challenge lane."),
        ),
        retainedWebsite(
            targetIp = RETAINED_NETWORK_SWITCHING_TOPIC_IP,
            title = "Network Switching",
            body = helpHtml("Network Switching", "The Network window shows allowed networks, NPCs, and the map you can travel through."),
        ),
        retainedWebsite(
            targetIp = RETAINED_PORT_MANAGEMENT_TOPIC_IP,
            title = "Port Management",
            body = helpHtml("Port Management", "Port Management is where you heal ports and install or replace programs and firewalls."),
        ),
        retainedWebsite(
            targetIp = RETAINED_ROOT_ATTACKER_CHALLENGE_TOPIC_IP,
            title = "Root Attacker",
            body = helpHtml("Root Attacker", "Root Attacker is an introductory challenge target for learning attack and redirect flows."),
        ),
        retainedWebsite(
            targetIp = RETAINED_MERCHANT_BANKER_CHALLENGE_TOPIC_IP,
            title = "Merchant Banker",
            body = helpHtml("Merchant Banker", "Merchant Banker ties together store income, bank ports, and petty-cash movement."),
        ),
    ).associateBy { it.targetStateId }

    override suspend fun loadHelpTopics(topicGroup: String): List<HelpTopicEntry> {
        val normalizedGroup = topicGroup.trim().ifBlank { HELP_TOPIC_GROUP_TUTORIALS }
        return topicsByGroup[normalizedGroup].orEmpty()
    }

    override suspend fun loadTutorial(tutorialId: String): TutorialResponse? {
        return tutorialsById[tutorialId]
    }

    override suspend fun loadWebsite(targetStateId: GameStateId): RetainedWebsitePage? {
        return websitesByStateId[targetStateId]
    }

    private fun helpTopic(
        id: String,
        name: String,
        targetIp: String,
    ): HelpTopicEntry {
        return HelpTopicEntry(
            name = name,
            id = id,
            targetUrl = "http://$targetIp/",
        )
    }

    private fun retainedWebsite(
        targetIp: String,
        title: String,
        body: String,
    ): RetainedWebsitePage {
        return RetainedWebsitePage(
            targetStateId = GameStateId(targetIp),
            title = title,
            body = body,
        )
    }

    private fun helpHtml(
        title: String,
        description: String,
    ): String {
        return """
            <html><body>
            <h1>$title</h1>
            <p>$description</p>
            </body></html>
        """.trimIndent()
    }
}

class RequestHelpTopicListCommand(
    private val requesterStateId: GameStateId,
    private val topicGroup: String?,
    private val repository: RetainedHelpTutorialRepository,
) : RequestCommand<HelpTopicListResponse> {
    override val name: String = "requesthelptopiclist"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(requesterStateId)

    override suspend fun execute(context: CommandContext): HelpTopicListResponse {
        val normalizedGroup = topicGroup?.takeUnless { it.isBlank() } ?: HELP_TOPIC_GROUP_TUTORIALS
        return HelpTopicListResponse(
            topicGroup = normalizedGroup,
            topics = repository.loadHelpTopics(normalizedGroup),
        )
    }
}

class RequestTutorialCommand(
    private val requesterStateId: GameStateId,
    private val tutorialId: String?,
    private val repository: RetainedHelpTutorialRepository,
) : RequestCommand<TutorialResponse> {
    override val name: String = "requesttutorial"
    override val lifetime: CommandLifetime = CommandLifetime.defaultRequest
    override val targetStateIds: Set<GameStateId> = setOf(requesterStateId)

    override suspend fun execute(context: CommandContext): TutorialResponse {
        val normalizedId = tutorialId?.takeUnless { it.isBlank() } ?: RETAINED_FIRST_ATTACK_TUTORIAL_ID
        return repository.loadTutorial(normalizedId)
            ?: error("No retained tutorial exists for id $normalizedId.")
    }
}

private const val HELP_TOPIC_GROUP_TUTORIALS: String = "Tutorials"
private const val HELP_TOPIC_GROUP_CHALLENGES: String = "Challenges"
private const val HELP_TOPIC_GROUP_BANKING: String = "Banking"
private const val HELP_TOPIC_GROUP_ATTACK: String = "Attack"
private const val HELP_TOPIC_GROUP_FTP: String = "FTP"
private const val HELP_TOPIC_GROUP_WATCH: String = "Watch"
private const val HELP_TOPIC_GROUP_CHALLENGE_API: String = "Challenge"
private const val HELP_TOPIC_GROUP_OTHER: String = "Other"

private const val RETAINED_FIRST_ATTACK_TOPIC_IP: String = "203.0.113.210"
private const val RETAINED_BANKING_DEPOSIT_TOPIC_IP: String = "203.0.113.211"
private const val RETAINED_BANKING_WITHDRAW_TOPIC_IP: String = "203.0.113.212"
private const val RETAINED_BANKING_TRANSFER_TOPIC_IP: String = "203.0.113.213"
private const val RETAINED_ATTACK_PORT_TOPIC_IP: String = "203.0.113.214"
private const val RETAINED_REDIRECT_PORT_TOPIC_IP: String = "203.0.113.215"
private const val RETAINED_ZOMBIE_ATTACK_TOPIC_IP: String = "203.0.113.216"
private const val RETAINED_SHOP_FTP_TOPIC_IP: String = "203.0.113.217"
private const val RETAINED_PUBLIC_FTP_TOPIC_IP: String = "203.0.113.218"
private const val RETAINED_WATCH_MANAGER_TOPIC_IP: String = "203.0.113.219"
private const val RETAINED_BOUNTY_TOPIC_IP: String = "203.0.113.220"
private const val RETAINED_NETWORK_SWITCHING_TOPIC_IP: String = "203.0.113.221"
private const val RETAINED_PORT_MANAGEMENT_TOPIC_IP: String = "203.0.113.222"
private const val RETAINED_ROOT_ATTACKER_CHALLENGE_TOPIC_IP: String = "203.0.113.223"
private const val RETAINED_MERCHANT_BANKER_CHALLENGE_TOPIC_IP: String = "203.0.113.224"
