package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.RepairEquipment
import game.ApplicationData
import game.payload.RepairEquipmentPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.REPAIREQUIPMENT)
object RepairEquipmentHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RepairEquipment.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(
                RepairEquipmentPayload(parsedCall.position ?: -1, parsedCall.name.orEmpty()),
                0,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
