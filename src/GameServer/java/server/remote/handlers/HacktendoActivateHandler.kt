package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(HacktendoActivate.FUNCTION)
object HacktendoActivateHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = HacktendoActivate.fromRpc(rfc)
        val activateID = parsedCall.activateID
        val activateType = parsedCall.activateType
        val ip = parsedCall.ip

        val O = arrayOf<Any>(activateID, activateType)
        context.computerHandler.addData(
            ApplicationData(HacktendoActivate.FUNCTION, O, 0, ip),
            ip,
            ApplicationData.INSIDE
        )
    }
}
