package game

import org.apache.xmlrpc.client.XmlRpcClient
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl
import game.payload.WebPagePayload
import util.LocalWebConfig
import util.zip
import java.net.URL

/**
 * NetworkSwitch.java
 *
 * Used to distribute function calls to the computers in the computer handling system.
 */
open class NetworkSwitch(
    private val myComputer: Computer?,
    private val myComputerHandler: ComputerHandler?
) {
    open fun getMyComputerHandler(): ComputerHandler {
        return myComputerHandler!!
    }

    /*
    The switch.
    */
    open fun addData(AD: ApplicationData, ip: String?) {
        if (ip == "062.153.7.142") {
            val payload = AD.payload
            if (payload is WebPagePayload) {
                println("Attempting to return site.")
                try {
                    val config = XmlRpcClientConfigImpl()
                    config.serverURL = URL(LocalWebConfig.getXmlRpcUrl())
                    val client = XmlRpcClient()
                    client.setConfig(config)
                    val params = arrayOf(payload.title, zip.zipString(payload.body), payload.packetId as Integer)
                    client.execute("hackerRPC.returnWebsite", params) as String
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else if (myComputer != null && myComputer.getIP() == ip) {
            myComputer.addData(AD)
        } else {
            myComputerHandler!!.addData(AD, ip)
        }
    }
}
