package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("setdummyport")
object SetDummyPortHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = SetDummyPort.fromRpc(rfc)
        var ip = parsedCall.ip
        ip = context.crypt(ip)
        val port = parsedCall.port
        val dummy = parsedCall.dummy
        context.computerHandler.addData(
            ApplicationData("setdummyport", dummy, port, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
