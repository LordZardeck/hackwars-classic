package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(InstallEquipment.FUNCTION)
object InstallEquipmentHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = InstallEquipment.fromRpc(rfc)
        var ip = parsedCall.ip
        val position =
            parsedCall.position
        val name = parsedCall.name
        ip = context.crypt(ip)
        val O: Array<Any?>? = arrayOf<Any?>(position, name, rfc.getID())
        context.computerHandler.addData(
            ApplicationData(
                InstallEquipment.FUNCTION,
                O,
                0,
                ip
            ), ip, ApplicationData.OUTSIDE
        )
    }
}
