package game

import org.apache.xmlrpc.client.XmlRpcClient
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl
import util.LocalWebConfig
import util.sql
import java.net.URL
import java.util.ArrayList

/**
 * Description: This class processes a single queued save request by talking to Tomcat/MySQL.
 * The coroutine scheduler now owns the worker lifecycle; this class only owns the save logic.
 */
open class CheckOutHandler {
    private var Connection = "127.0.0.1"
    private var DB = "hackwars"
    private var Username = "root"
    private var Password = ""

    //Fetches a profile from the DB based on IP.
    open fun fetchProfile(ip: String, checkActive: Boolean): String? {
        val c = sql(Connection, DB, Username, Password)
        var result: ArrayList<*>? = null
        try {
            if (checkActive) {
                val query = "SELECT npc,TO_DAYS(NOW())-TO_DAYS(last_logged_in) FROM hackerforum.users WHERE ip=\"$ip\""
                result = c.process(query)
                if (result == null) {
                    return null
                }
                val npc = result[0] as String
                val active = result[1] as String
                if (npc == "N") {
                    if (Integer.parseInt(active) > 14) {
                        return "inactive"
                    }
                }
            }
            val q = "select stats from user where ip=\"$ip\""
            result = c.process(q)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                c.close()
            } catch (_: Exception) {
            }
        }

        return if (result == null) {
            null
        } else {
            result[0] as String
        }
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
        val c = sql(Connection, DB, Username, Password)
        val q = "insert into user values(\"$ip\",\"${content.replace("\"", "\\\\\"")}\",NULL);"
        c.process(q)
        c.close()
    }

    protected open fun updateProfile(ip: String, content: String) {
        val c = sql(Connection, DB, Username, Password)
        val escapedContent = content.replace("\\", "\\\\").replace("\"", "\\\\\"")
        val q = "update user set stats=\"$escapedContent\" where ip=\"$ip\";"

        println("Just got to the process step.")

        c.process(q)
        c.close()
    }
}
