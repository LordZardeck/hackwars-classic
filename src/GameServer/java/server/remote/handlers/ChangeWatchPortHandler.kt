package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(ChangeWatchPort.FUNCTION)
object ChangeWatchPortHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = ChangeWatchPort.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)

        context.computerHandler.addData(
            ApplicationData(
                parsedCall,
                0,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
