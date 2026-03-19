package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RepairEquipment.FUNCTION)
object RepairEquipmentHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RepairEquipment.fromRpc(rfc)
        var ip = parsedCall.ip
        val position =
            parsedCall.position
        val name =
            parsedCall.name
        ip = context.crypt(ip)
        val O: Array<Any?>? =
            arrayOf<Any?>(position, name, rfc.getID())
        context.computerHandler.addData(
            ApplicationData(
                RepairEquipment.FUNCTION,
                O,
                0,
                ip
            ), ip, ApplicationData.OUTSIDE
        )
    }
}
