package com.hackwars.rewrite.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RewriteClientJsonTest {
    @Test
    fun decodesShellSnapshotPayloadsAndIgnoresUnknownFields() {
        val payload = """
            {
              "id":"LOCAL-IP",
              "version":7,
              "identity":{
                "playFabId":"PF-LOCAL",
                "playerIp":"LOCAL-IP",
                "displayName":"Local User",
                "isNpc":false,
                "ignoredField":"ignored"
              },
              "economy":{
                "pettyCash":125.0,
                "bankMoney":25.0,
                "commodities":[1.0,2.0,3.0,4.0,5.0],
                "defaultBankPort":4,
                "defaultRedirectPort":9,
                "commodityRespawn":[5.0,4.0,3.0,2.0,1.0]
              },
              "hardware":{
                "cpuType":3,
                "cpuMax":50.0,
                "memoryType":2,
                "hdType":1,
                "hdQuantity":20,
                "hdMaximum":100,
                "equipmentSlots":{
                  "CPU":{
                    "slot":"CPU",
                    "name":"Turbo CPU",
                    "cpuBoost":12.0,
                    "ignoredNested":"ignored"
                  }
                }
              },
              "ports":[
                {
                  "number":4,
                  "type":"Bank",
                  "defaultPort":true,
                  "health":100.0,
                  "installedApplication":{"name":"Bank","kind":"BANKING"},
                  "installedFirewall":{"name":"Basic","kind":"BASIC"},
                  "ignoredPortField":"ignored"
                }
              ],
              "website":{"title":"Homepage","body":"Welcome","voteCount":3,"votesAvailable":1},
              "preferences":{"values":{"show_tutorials":"true"}},
              "stats":{"experienceByFamily":{"ATTACK":10.0},"totalLevel":2,"noobProtectionLevel":1},
              "logs":{"entries":[{"createdAtEpochMillis":1,"renderedLine":"Hello","sourceIp":"TARGET-IP"}]},
              "runtime":{"countdownSeconds":12,"currentCpuLoad":8.5},
              "unknownRootField":"ignored"
            }
        """.trimIndent().encodeToByteArray()

        val decoded = RewriteClientJson.decode(ClientGameSnapshot.serializer(), payload)

        assertEquals("LOCAL-IP", decoded.id)
        assertEquals(7, decoded.version)
        assertEquals("PF-LOCAL", decoded.identity.playFabId)
        assertEquals(125.0, decoded.economy.pettyCash)
        assertEquals(4, decoded.economy.defaultBankPort)
        assertEquals(1, decoded.ports.size)
        assertEquals("Bank", decoded.ports.single().installedApplication?.name)
        assertEquals("Homepage", decoded.website.title)
        assertEquals("true", decoded.preferences.values["show_tutorials"])
        assertEquals(12, decoded.runtime.countdownSeconds)
    }

    @Test
    fun decodesDeltaProgramAndUiPayloadsFromCurrentRewriteShape() {
        val deltaPayload = """
            {
              "type":"state_sections",
              "economy":{"pettyCash":250.0,"bankMoney":15.0},
              "runtime":{"countdownSeconds":11,"currentCpuLoad":9.0},
              "extraSection":"ignored"
            }
        """.trimIndent().encodeToByteArray()
        val summaryPayload = """
            {
              "type":"state_summary",
              "version":9,
              "playerIp":"LOCAL-IP",
              "extra":"ignored"
            }
        """.trimIndent().encodeToByteArray()
        val programPayload = """
            {
              "programId":"program-1",
              "programType":"attack",
              "status":"RUNNING",
              "relatedStateIds":["LOCAL-IP","TARGET-IP"],
              "progress":{"message":"tick","completedSteps":1,"totalSteps":3},
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()
        val popupPayload = """
            {
              "type":"popup",
              "message":"Warning",
              "style":"ERROR",
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()
        val showChoicesPayload = """
            {
              "type":"show_choices",
              "targetIp":"TARGET-IP",
              "targetPort":6,
              "choiceType":"HTTP",
              "windowHandle":99
            }
        """.trimIndent().encodeToByteArray()

        val delta = RewriteClientJson.decode(ClientGameDeltaProjection.serializer(), deltaPayload)
        val summary = RewriteClientJson.decode(ClientGameDeltaProjection.serializer(), summaryPayload)
        val program = RewriteClientJson.decode(ClientProgramUpdate.serializer(), programPayload)
        val popup = RewriteClientJson.decode(ClientGameUiEvent.serializer(), popupPayload)
        val showChoices = RewriteClientJson.decode(ClientGameUiEvent.serializer(), showChoicesPayload)

        assertIs<ClientGameSectionsProjection>(delta)
        assertEquals(250.0, delta.economy?.pettyCash)
        assertIs<ClientGameStateSummaryProjection>(summary)
        assertEquals(9, summary.version)
        assertEquals(ClientProgramLifecycleStatus.RUNNING, program.status)
        assertEquals(setOf("LOCAL-IP", "TARGET-IP"), program.relatedStateIds)
        assertIs<ClientPopupUiEvent>(popup)
        assertEquals(ClientPopupUiStyle.ERROR, popup.style)
        assertIs<ClientShowChoicesUiEvent>(showChoices)
        assertEquals(ClientShowChoicesType.HTTP, showChoices.choiceType)
    }

    @Test
    fun decodesCurrentRewriteUiEventVariants() {
        val attackMessage = RewriteClientJson.decode(
            ClientGameUiEvent.serializer(),
            """
                {
                  "type":"attack_message",
                  "message":"Redirect receipt",
                  "port":9,
                  "ip":"TARGET-IP",
                  "windowHandle":44,
                  "paneType":"REDIRECT"
                }
            """.trimIndent().encodeToByteArray(),
        )
        val zombieAttack = RewriteClientJson.decode(
            ClientGameUiEvent.serializer(),
            """
                {
                  "type":"zombie_attack",
                  "message":"Computer at ZOMBIE-IP just overheated!",
                  "zombieIp":"ZOMBIE-IP",
                  "sourcePort":8
                }
            """.trimIndent().encodeToByteArray(),
        )
        val textMessage = RewriteClientJson.decode(
            ClientGameUiEvent.serializer(),
            """
                {
                  "type":"message",
                  "message":"Daily pay successfully changed."
                }
            """.trimIndent().encodeToByteArray(),
        )

        assertIs<ClientAttackMessageUiEvent>(attackMessage)
        assertEquals(ClientAttackPaneType.REDIRECT, attackMessage.paneType)
        assertIs<ClientZombieAttackUiEvent>(zombieAttack)
        assertEquals("ZOMBIE-IP", zombieAttack.zombieIp)
        assertIs<ClientTextMessageUiEvent>(textMessage)
        assertTrue(textMessage.message.contains("Daily pay"))
    }
}
