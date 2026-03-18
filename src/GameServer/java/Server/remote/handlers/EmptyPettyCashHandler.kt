package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("emptypettycash")
object EmptyPettyCashHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = EmptyPettyCash.fromRpc(rfc)
        var ip = parsedCall.ip
        ip = context.crypt(ip)
        val targetIP = parsedCall.targetIP
        val targetPort = parsedCall.targetPort
        val windowHandle = parsedCall.windowHandle
        context.computerHandler.addData(
            ApplicationData("emptyPettyCash", windowHandle, targetPort, ip),
            targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
