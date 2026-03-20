package game

import com.hackwars.data.service.GameAuthDataService
import com.hackwars.data.service.GameProfileDataService
import game.data.GameServerDataLocator
import org.apache.xmlrpc.client.XmlRpcClient
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl
import util.LocalWebConfig
import java.net.URL

/**
 * Description: This class processes a single queued save request by talking to Tomcat/MySQL.
 * The coroutine scheduler now owns the worker lifecycle; this class only owns the save logic.
 */
open class CheckOutHandler(
    private val authDataService: GameAuthDataService? = null,
    private val profileDataService: GameProfileDataService? = null,
) {
    private fun authDataService(): GameAuthDataService = authDataService ?: GameServerDataLocator.authService()

    private fun profileDataService(): GameProfileDataService = profileDataService ?: GameServerDataLocator.profileService()

    //Fetches a profile from the DB based on IP.
    open fun fetchProfile(ip: String, checkActive: Boolean): String? {
        try {
            if (checkActive) {
                val activity = authDataService().findForumActivityByIp(ip) ?: return null
                if (activity.npc == "N" && (activity.daysSinceLastLogin ?: 0) > 14) {
                    return "inactive"
                }
            }
            val result = profileDataService().findProfileXmlByIp(ip)
            if (result == null) {
                return null
            }
            return result
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    open fun processWork(o: Array<Any?>) {
        val ip = o[0] as String
        val computer = o[1] as Computer
        var content = readComputerOutput(computer)
        val pageChanged = o[3] as Boolean
        val pageTitle = o[4] as String
        val pageBody = o[5] as String

        println("Starting Saving $ip")

        try {
            if (pageChanged) {
                println("Page Changed!")
                saveProfileViaXmlRpc(ip, content, pageChanged, pageTitle, pageBody)
            } else {
                if (fetchProfile(ip, false) == null) {
                    insertProfile(ip, content)
                } else {
                    updateProfile(ip, content)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        System.gc()
        println("Finished Saving $ip")
    }

    protected open fun readComputerOutput(computer: Computer): String {
        return computer.outputXML()
    }

    protected open fun saveProfileViaXmlRpc(
        ip: String,
        content: String,
        pageChanged: Boolean,
        pageTitle: String,
        pageBody: String
    ) {
        val config = XmlRpcClientConfigImpl()
        config.serverURL = URL(LocalWebConfig.getXmlRpcUrl())
        val client = XmlRpcClient()
        client.setConfig(config)
        println("xmlrpc started")

        val params = arrayOf("asdbas0d98a0sd9fa8sasdlbo", ip, content, java.lang.Boolean(pageChanged), pageTitle, pageBody)
        client.execute("hackerRPC.saveProfile", params)
    }

    protected open fun insertProfile(ip: String, content: String) {
        try {
            profileDataService().upsertProfileXmlByIp(ip, content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    protected open fun updateProfile(ip: String, content: String) {
        try {
            profileDataService().upsertProfileXmlByIp(ip, content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
