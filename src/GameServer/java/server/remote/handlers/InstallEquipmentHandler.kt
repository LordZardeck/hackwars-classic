package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.InstallEquipment
import game.ApplicationData
import game.payload.InstallEquipmentPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.INSTALLEQUIPMENT)
object InstallEquipmentHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = InstallEquipment.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(
                InstallEquipmentPayload(parsedCall.position ?: -1, parsedCall.name.orEmpty(), rfc.getID()),
                0,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
