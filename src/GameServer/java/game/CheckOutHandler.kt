package game

import com.hackwars.data.service.GameAuthDataService
import com.hackwars.data.service.GameProfileDataService
import game.data.GameServerDataLocator

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
        val content = readComputerOutput(computer)
        val pageChanged = o[3] as Boolean
        o[4] as String
        o[5] as String

        println("Starting Saving $ip")

        try {
            if (pageChanged) {
                // TODO: Removed legacy remote profile save endpoint: http://127.0.0.1:8080/hackwars/xmlrpc -> hackerRPC.saveProfile
            }
            if (fetchProfile(ip, false) == null) {
                insertProfile(ip, content)
            } else {
                updateProfile(ip, content)
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
