package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.RepairEquipmentPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RepairEquipment.FUNCTION)
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
