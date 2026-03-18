package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(ChangeWatchPort.FUNCTION)
object ChangeWatchPortHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = ChangeWatchPort.fromRpc(rfc)

        context.computerHandler.addData(
            ApplicationData(
                ChangeWatchPort.FUNCTION,
                arrayOf(parsedCall.watchId, parsedCall.portId),
                0,
                context.crypt(parsedCall.ip)
            ),
            context.crypt(parsedCall.ip),
            ApplicationData.OUTSIDE
        )
    }
}
