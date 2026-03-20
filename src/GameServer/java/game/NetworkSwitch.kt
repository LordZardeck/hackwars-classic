package game

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
        // TODO: Removed legacy remote website relay endpoint: http://127.0.0.1:8080/hackwars/xmlrpc -> hackerRPC.returnWebsite
        if (myComputer != null && myComputer.getIP() == ip) {
            myComputer.addData(AD)
        } else {
            myComputerHandler!!.addData(AD, ip)
        }
    }
}
