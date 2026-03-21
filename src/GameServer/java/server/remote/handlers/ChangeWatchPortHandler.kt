package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.ChangeWatchPort
import game.ApplicationData
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.CHANGEWATCHPORT)
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
