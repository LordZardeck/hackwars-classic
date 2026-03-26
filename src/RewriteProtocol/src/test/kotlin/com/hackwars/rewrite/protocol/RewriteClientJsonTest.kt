package com.hackwars.rewrite.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
              "filesystem":{
                "currentPath":"/Public",
                "directoriesByPath":{
                  "/Public":{
                    "path":"/Public",
                    "name":"Public",
                    "description":"public docs"
                  }
                },
                "filesByPath":{
                  "/Public/http.bin":{
                    "path":"/Public/http.bin",
                    "name":"http.bin",
                    "kind":"APPLICATION_BINARY",
                    "contents":"compiled",
                    "compiledBinary":{
                      "scriptFamily":"HTTP",
                      "outputName":"http",
                      "applicationKind":"HTTP",
                      "healModifierDelta":1
                    },
                    "scriptBundle":{
                      "family":"HTTP",
                      "scriptsBySlot":{
                        "ENTER":"logMessage(\"hello\")"
                      }
                    },
                    "saveMetadata":{
                      "valuesByKey":{
                        "stage":{"type":"string","value":"starter"}
                      }
                    }
                  }
                }
              },
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
        assertEquals("/Public", decoded.filesystem.currentPath)
        assertTrue(decoded.filesystem.directoriesByPath.containsKey("/Public"))
        assertEquals("HTTP", decoded.filesystem.filesByPath["/Public/http.bin"]?.compiledBinary?.applicationKind?.name)
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
    fun decodesCurrentRewriteDirectoryListingResponseShape() {
        val payload = """
            {
              "stateId":"LOCAL-IP",
              "path":"/Public",
              "directories":[
                {
                  "path":"/Public/Archive",
                  "name":"Archive",
                  "description":"older files"
                }
              ],
              "files":[
                {
                  "path":"/Public/readme.txt",
                  "name":"readme.txt",
                  "kind":"TEXT",
                  "contents":"hello",
                  "description":"welcome",
                  "ignored":"ignored"
                }
              ],
              "version":14,
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()

        val response = RewriteClientJson.decode(
            ClientDirectoryListingResponse.serializer(),
            payload,
        )

        assertEquals("LOCAL-IP", response.stateId)
        assertEquals("/Public", response.path)
        assertEquals("/Public/Archive", response.directories.single().path)
        assertEquals("readme.txt", response.files.single().name)
        assertEquals(14, response.version)
    }

    @Test
    fun decodesCurrentRewriteRequestFileResponseShapeIncludingMissingFiles() {
        val filePayload = """
            {
              "stateId":"LOCAL-IP",
              "file":{
                "path":"/Public/readme.txt",
                "name":"readme.txt",
                "kind":"TEXT",
                "contents":"hello",
                "description":"welcome",
                "maker":"LOCAL-IP",
                "price":12.5,
                "cpuCost":1.25,
                "quantity":2,
                "ignored":"ignored"
              },
              "version":15,
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()
        val missingPayload = """
            {
              "stateId":"LOCAL-IP",
              "file":null,
              "version":16,
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()

        val response = RewriteClientJson.decode(
            ClientFileContentsResponse.serializer(),
            filePayload,
        )
        val missing = RewriteClientJson.decode(
            ClientFileContentsResponse.serializer(),
            missingPayload,
        )

        assertEquals("LOCAL-IP", response.stateId)
        assertEquals("/Public/readme.txt", response.file?.path)
        assertEquals("TEXT", response.file?.kind?.name)
        assertEquals(15, response.version)
        assertEquals("LOCAL-IP", missing.stateId)
        assertEquals(null, missing.file)
        assertEquals(16, missing.version)
    }

    @Test
    fun decodesCurrentRewriteSaveCompileAndDecompileResponseShapes() {
        val savePayload = """
            {
              "stateId":"LOCAL-IP",
              "version":17,
              "message":"file-saved",
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()
        val compilePayload = """
            {
              "stateId":"LOCAL-IP",
              "compiledFile":{
                "path":"/Scripts/attack.bin",
                "name":"attack.bin",
                "kind":"APPLICATION_BINARY",
                "compiledBinary":{
                  "scriptFamily":"ATTACK",
                  "applicationKind":"ATTACK"
                }
              },
              "pettyCashAfter":77.5,
              "experienceAfter":12.0,
              "version":18,
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()
        val decompilePayload = """
            {
              "stateId":"LOCAL-IP",
              "decompiledFile":{
                "path":"/Scripts/attack.src",
                "name":"attack.src",
                "kind":"SCRIPT_SOURCE",
                "scriptBundle":{
                  "family":"ATTACK",
                  "scriptsBySlot":{
                    "INITIALIZE":"init()"
                  }
                }
              },
              "pettyCashAfter":90.0,
              "experienceAfter":11.0,
              "version":19,
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()

        val saveResponse = RewriteClientJson.decode(
            ClientMutationAcceptedResponse.serializer(),
            savePayload,
        )
        val compileResponse = RewriteClientJson.decode(
            ClientCompileFileResponse.serializer(),
            compilePayload,
        )
        val decompileResponse = RewriteClientJson.decode(
            ClientDecompileFileResponse.serializer(),
            decompilePayload,
        )

        assertEquals("LOCAL-IP", saveResponse.stateId)
        assertEquals("file-saved", saveResponse.message)
        assertEquals(17, saveResponse.version)
        assertEquals("attack.bin", compileResponse.compiledFile.name)
        assertEquals(ClientApplicationKind.ATTACK, compileResponse.compiledFile.compiledBinary?.applicationKind)
        assertEquals(77.5, compileResponse.pettyCashAfter)
        assertEquals("attack.src", decompileResponse.decompiledFile.name)
        assertEquals(ClientScriptFamily.ATTACK, decompileResponse.decompiledFile.scriptBundle?.family)
        assertEquals(19, decompileResponse.version)
    }

    @Test
    fun decodesCurrentRewriteRequestPageAndSavePageResponseShapes() {
        val requestPagePayload = """
            {
              "stateId":"LOCAL-IP",
              "title":"Local Homepage",
              "body":"<h1>Hello</h1>",
              "version":20,
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()
        val savePagePayload = """
            {
              "stateId":"LOCAL-IP",
              "title":"Updated Homepage",
              "body":"<center>Updated</center>",
              "version":21,
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()

        val requestPageResponse = RewriteClientJson.decode(
            ClientPageEditorResponse.serializer(),
            requestPagePayload,
        )
        val savePageResponse = RewriteClientJson.decode(
            ClientSavePageResponse.serializer(),
            savePagePayload,
        )

        assertEquals("LOCAL-IP", requestPageResponse.stateId)
        assertEquals("Local Homepage", requestPageResponse.title)
        assertEquals("<h1>Hello</h1>", requestPageResponse.body)
        assertEquals(20, requestPageResponse.version)
        assertEquals("LOCAL-IP", savePageResponse.stateId)
        assertEquals("Updated Homepage", savePageResponse.title)
        assertEquals("<center>Updated</center>", savePageResponse.body)
        assertEquals(21, savePageResponse.version)
    }

    @Test
    fun decodesCurrentRewriteMakeBountyResponseShape() {
        val payload = """
            {
              "creatorStateId":"LOCAL-IP",
              "storeStateId":"store1",
              "bountyFile":{
                "path":"/Store/LOCAL-IP-install-1.bnty",
                "name":"LOCAL-IP-install-1.bnty",
                "kind":"BOUNTY",
                "contents":"bounty",
                "description":"Install bounty",
                "bountyMetadata":{
                  "type":2,
                  "target":"ENEMY-IP",
                  "iterationsRemaining":3,
                  "reward":125.0,
                  "bountySourceStateId":"LOCAL-IP",
                  "requiredMaker":"LOCAL-IP",
                  "requiredScriptName":"installer.bin",
                  "anonymous":false
                },
                "ignored":"ignored"
              },
              "reward":125.0,
              "creatorVersion":12,
              "storeVersion":25,
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()

        val response = RewriteClientJson.decode(
            ClientBountyCreatedResponse.serializer(),
            payload,
        )

        assertEquals("LOCAL-IP", response.creatorStateId)
        assertEquals("store1", response.storeStateId)
        assertEquals("/Store/LOCAL-IP-install-1.bnty", response.bountyFile.path)
        assertEquals(125.0, response.reward)
        assertEquals(12, response.creatorVersion)
        assertEquals(25, response.storeVersion)
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

    @Test
    fun decodesCurrentRewriteEconomyCommandResponsePayloads() {
        val bankTransactionPayload = """
            {
              "stateId":"LOCAL-IP",
              "operation":"deposit",
              "portNumber":4,
              "requestedAmount":600.0,
              "appliedAmount":500.0,
              "pettyCashAfter":0.0,
              "bankMoneyAfter":700.0,
              "version":12,
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()
        val transferPayload = """
            {
              "sourceStateId":"LOCAL-IP",
              "targetStateId":"TARGET-IP",
              "portNumber":4,
              "requestedAmount":125.0,
              "appliedAmount":125.0,
              "sourcePettyCashAfter":375.0,
              "targetPettyCashAfter":175.0,
              "sourceVersion":10,
              "targetVersion":9,
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()

        val bankTransaction = RewriteClientJson.decode(
            ClientBankTransactionResponse.serializer(),
            bankTransactionPayload,
        )
        val transfer = RewriteClientJson.decode(
            ClientTransferResponse.serializer(),
            transferPayload,
        )

        assertEquals("LOCAL-IP", bankTransaction.stateId)
        assertEquals("deposit", bankTransaction.operation)
        assertEquals(500.0, bankTransaction.appliedAmount)
        assertEquals("LOCAL-IP", transfer.sourceStateId)
        assertEquals("TARGET-IP", transfer.targetStateId)
        assertEquals(125.0, transfer.appliedAmount)
    }

    @Test
    fun decodesCurrentRewriteWebAndStoreCommandPayloadsAndResponses() {
        val webpagePayload = """
            {
              "resolvedTargetStateId":"TARGET-IP",
              "title":"Remote Shop",
              "body":"<html><body><a href=\"?buy=1\">Buy</a></body></html>",
              "storeFiles":[
                {
                  "path":"/Store/attack.bin",
                  "name":"attack.bin",
                  "kind":"APPLICATION_BINARY",
                  "maker":"TARGET-IP",
                  "price":25.0,
                  "quantity":3,
                  "compiledBinary":{
                    "scriptFamily":"ATTACK",
                    "applicationKind":"ATTACK"
                  }
                }
              ],
              "fallback":false,
              "version":21,
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()
        val purchasePayload = """
            {
              "buyerStateId":"LOCAL-IP",
              "sellerStateId":"TARGET-IP",
              "revenueTargetStateId":"TARGET-IP",
              "purchasedFile":{
                "path":"/Store/attack.bin",
                "name":"attack.bin",
                "kind":"APPLICATION_BINARY"
              },
              "fulfilledQuantity":2,
              "totalPrice":50.0,
              "buyerVersion":22,
              "sellerVersion":23,
              "revenueTargetVersion":24,
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()
        val votePayload = """
            {
              "voterStateId":"LOCAL-IP",
              "targetStateId":"TARGET-IP",
              "votesAvailableAfter":1,
              "targetVoteCountAfter":4,
              "targetHttpExperienceAfter":12.5,
              "voterVersion":25,
              "targetVersion":26,
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()

        val webpageResponse = RewriteClientJson.decode(
            ClientWebsiteRenderResponse.serializer(),
            webpagePayload,
        )
        val purchaseResponse = RewriteClientJson.decode(
            ClientPurchaseResponse.serializer(),
            purchasePayload,
        )
        val voteResponse = RewriteClientJson.decode(
            ClientVoteResponse.serializer(),
            votePayload,
        )

        assertEquals("TARGET-IP", webpageResponse.resolvedTargetStateId)
        assertEquals("Remote Shop", webpageResponse.title)
        assertEquals("attack.bin", webpageResponse.storeFiles.single().name)
        assertFalse(webpageResponse.fallback)
        assertEquals(21, webpageResponse.version)

        assertEquals("LOCAL-IP", purchaseResponse.buyerStateId)
        assertEquals("TARGET-IP", purchaseResponse.sellerStateId)
        assertEquals(2, purchaseResponse.fulfilledQuantity)
        assertEquals(50.0, purchaseResponse.totalPrice)

        assertEquals("LOCAL-IP", voteResponse.voterStateId)
        assertEquals("TARGET-IP", voteResponse.targetStateId)
        assertEquals(1, voteResponse.votesAvailableAfter)
        assertEquals(4, voteResponse.targetVoteCountAfter)
    }

    @Test
    fun decodesCurrentRewriteHealPortResponseShape() {
        val payload = """
            {
              "stateId":"LOCAL-IP",
              "portNumber":6,
              "accepted":true,
              "outcome":"SUCCESS",
              "message":"healport-succeeded",
              "chargedAmount":5.0,
              "pettyCashAfter":20.0,
              "healthAfter":100.0,
              "healCountAfter":1,
              "version":22,
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()

        val response = RewriteClientJson.decode(
            ClientHealPortResponse.serializer(),
            payload,
        )

        assertEquals("LOCAL-IP", response.stateId)
        assertTrue(response.accepted)
        assertEquals(ClientHealPortOutcome.SUCCESS, response.outcome)
        assertEquals(6, response.portNumber)
        assertEquals(22, response.version)
    }

    @Test
    fun decodesCurrentRewriteInstallApplicationAndFirewallResponses() {
        val applicationPayload = """
            {
              "stateId":"LOCAL-IP",
              "portNumber":6,
              "installedApplication":{
                "name":"bank.bin",
                "kind":"BANKING",
                "cpuCost":2.5,
                "ignored":"ignored"
              },
              "defaultBankPort":6,
              "version":23,
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()
        val firewallPayload = """
            {
              "stateId":"LOCAL-IP",
              "portNumber":6,
              "installedFirewall":{
                "name":"guard.fw",
                "kind":"BASIC",
                "cpuCost":1.5,
                "ignored":"ignored"
              },
              "returnedFirewall":null,
              "version":24,
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()

        val applicationResponse = RewriteClientJson.decode(
            ClientInstallApplicationResponse.serializer(),
            applicationPayload,
        )
        val firewallResponse = RewriteClientJson.decode(
            ClientInstallFirewallResponse.serializer(),
            firewallPayload,
        )

        assertEquals("bank.bin", applicationResponse.installedApplication.name)
        assertEquals(6, applicationResponse.defaultBankPort)
        assertEquals("guard.fw", firewallResponse.installedFirewall.name)
        assertEquals(24, firewallResponse.version)
    }
}
