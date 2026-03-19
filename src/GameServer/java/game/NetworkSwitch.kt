package game

import org.apache.xmlrpc.client.XmlRpcClient
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl
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
            if (AD.getFunction() == "webpage") {
                println("Attempting to return site.")

                val o = AD.getParameters() as Array<*>
                try {
                    val config = XmlRpcClientConfigImpl()
                    config.serverURL = URL(LocalWebConfig.getXmlRpcUrl())
                    val client = XmlRpcClient()
                    client.setConfig(config)
                    val params = arrayOf(o[0] as String, zip.zipString(o[1] as String), o[3] as Integer)
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
