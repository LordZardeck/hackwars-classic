package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.InstallEquipmentPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(InstallEquipment.FUNCTION)
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
