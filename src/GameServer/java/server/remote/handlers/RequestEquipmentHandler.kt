package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.RequestEquipment
import game.ApplicationData
import game.payload.RequestEquipmentPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.REQUESTEQUIPMENT)
object RequestEquipmentHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestEquipment.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(RequestEquipmentPayload(rfc.getID()), 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
