package game

import org.apache.xmlrpc.client.XmlRpcClient
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl
import util.LocalWebConfig
import util.sql
import java.net.URL
import java.util.ArrayList

/**
 * Description: This class looks for data to be saved and makes a connection to the Tomcat server/MySQL and
 * saves a profile.
 */
open class CheckOutHandler : Runnable {
    private var Connection = "127.0.0.1"
    private var DB = "hackwars"
    private var Username = "root"
    private var Password = ""
    private var lastSave = 0L
    private var MyThread: Thread? = null
    private var running = false

    init {
        this.start()
    }

    private fun start() {
        MyThread = Thread(this, "CheckOutHandler")
        running = true
        MyThread!!.start()
    }

    //Fetches a profile from the DB based on IP.
    fun fetchProfile(ip: String, checkActive: Boolean): String? {
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
            c.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return if (result == null) {
            null
        } else {
            result[0] as String
        }
    }

    //Thread processes current save tasks.
    @Synchronized
    override fun run() {
        while (running) {
            try {
                val o = MysqlHandler.getWork()
                if (o != null) {
                    val ip = o[0] as String
                    val computer = o[1] as Computer
                    var content = computer.outputXML()
                    val pageChanged = o[3] as Boolean
                    val pageTitle = o[4] as String
                    val pageBody = o[5] as String

                    println("Starting Saving $ip")

                    try {
                        if (pageChanged) {
                            println("Page Changed!")

                            val config = XmlRpcClientConfigImpl()
                            config.serverURL = URL(LocalWebConfig.getXmlRpcUrl())
                            val client = XmlRpcClient()
                            client.setConfig(config)
                            println("xmlrpc started")

                            val params = arrayOf("asdbas0d98a0sd9fa8sasdlbo", ip, content, java.lang.Boolean(pageChanged), pageTitle, pageBody)
                            client.execute("hackerRPC.saveProfile", params)
                        } else {
                            if (fetchProfile(ip, false) == null) {
                                val c = sql(Connection, DB, Username, Password)
                                val q = "insert into user values(\"$ip\",\"${content.replace("\"", "\\\\\"")}\",NULL);"
                                c.process(q)
                                c.close()
                            } else {
                                val c = sql(Connection, DB, Username, Password)
                                content = content.replace("\\", "\\\\")
                                content = content.replace("\"", "\\\\\"")

                                val q = "update user set stats=\"$content\" where ip=\"$ip\";"

                                println("Just got to the process step.")

                                c.process(q)
                                c.close()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    SAVE_COUNTER--
                    println("Finished Saving $ip, accounts left to save = $SAVE_COUNTER")
                    System.gc()
                    lastSave = ServerRuntimeState.now()
                }

                if ((!ServerRuntimeState.isRunning() && SAVE_COUNTER == 0 && ServerRuntimeState.now() - lastSave > maxZeroTimeout) || (!ServerRuntimeState.isRunning() && ServerRuntimeState.now() - ServerRuntimeState.getShutdownAt() > maxTimeout)) {
                    System.exit(0)
                }

                Thread.sleep(sleepTime)
            } catch (e: Exception) {
            }
        }
    }

    companion object {
        @JvmField
        var SAVE_COUNTER = 0

        private const val maxTimeout = 350000L
        private const val maxZeroTimeout = 20000L
        private const val sleepTime = 500L
    }
}
