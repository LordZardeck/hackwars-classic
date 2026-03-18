package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RequestEquipment.FUNCTION)
object RequestEquipmentHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestEquipment.fromRpc(rfc)
        var ip = parsedCall.ip
        ip = context.crypt(ip)
        context.computerHandler.addData(
            ApplicationData(
                RequestEquipment.FUNCTION,
                rfc.getID(),
                0,
                ip
            ), ip, ApplicationData.OUTSIDE
        )
    }
}
