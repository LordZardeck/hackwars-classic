package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(ClueData.FUNCTION)
object ClueDataHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            ClueData.fromRpc(
                rfc
            )
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val data =
            parsedCall.data
        context.computerHandler.addData(
            ApplicationData(
                ClueData.FUNCTION,
                data,
                0,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
