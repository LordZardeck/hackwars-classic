package com.hackwars.rewrite.gameserver

import com.hackwars.rewrite.protocol.ClientHelpTopicListResponse
import com.hackwars.rewrite.protocol.ClientRequestHelpTopicListPayload
import com.hackwars.rewrite.protocol.ClientRequestTutorialPayload
import com.hackwars.rewrite.protocol.ClientTutorialResponse
import com.hackwars.rewrite.protocol.RewriteClientJson
import kotlin.test.Test
import kotlin.test.assertEquals

class RewriteGameHelpTutorialProtocolAdapterTest {
    @Test
    fun helpTopicListResponseRetainsTopicListAndWebpageTargets() {
        val payload = """
            {
              "topicGroup":"APIs",
              "topics":[
                {
                  "name":"Banking",
                  "id":"12",
                  "targetUrl":"http://help/help.php?id=12",
                  "ignored":"ignored"
                },
                {
                  "name":"Attack",
                  "id":"19",
                  "targetUrl":"http://help/help.php?id=19"
                }
              ],
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()

        val response = RewriteClientJson.decode(
            ClientHelpTopicListResponse.serializer(),
            payload,
        )
        val request = RewriteClientJson.decode(
            ClientRequestHelpTopicListPayload.serializer(),
            RewriteClientJson.encode(
                serializer = ClientRequestHelpTopicListPayload.serializer(),
                value = ClientRequestHelpTopicListPayload(topicGroup = "APIs"),
            ),
        )

        assertEquals("APIs", response.topicGroup)
        assertEquals(listOf("Banking", "Attack"), response.topics.map { it.name })
        assertEquals("http://help/help.php?id=19", response.topics.last().targetUrl)
        assertEquals("APIs", request.topicGroup)
    }

    @Test
    fun tutorialFetchResponseRetainsInitialContentAndTitle() {
        val payload = """
            {
              "tutorialId":"first-attack",
              "title":"First Attack",
              "body":"<p>Welcome</p>",
              "ignored":"ignored"
            }
        """.trimIndent().encodeToByteArray()

        val response = RewriteClientJson.decode(
            ClientTutorialResponse.serializer(),
            payload,
        )
        val request = RewriteClientJson.decode(
            ClientRequestTutorialPayload.serializer(),
            RewriteClientJson.encode(
                serializer = ClientRequestTutorialPayload.serializer(),
                value = ClientRequestTutorialPayload(tutorialId = "first-attack"),
            ),
        )

        assertEquals("first-attack", response.tutorialId)
        assertEquals("First Attack", response.title)
        assertEquals("<p>Welcome</p>", response.body)
        assertEquals("first-attack", request.tutorialId)
    }
}
