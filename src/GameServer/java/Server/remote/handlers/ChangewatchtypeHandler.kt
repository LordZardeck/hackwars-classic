package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(ChangeWatchType.FUNCTION)
object ChangeWatchTypeHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            ChangeWatchType.fromRpc(
                rfc
            )
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val WatchID =
            parsedCall.watchID
        val PortID =
            parsedCall.portID
        val I: Array<Int?>? =
            arrayOf<Int?>(
                WatchID,
                PortID
            )
        context.computerHandler.addData(
            ApplicationData(
                ChangeWatchType.FUNCTION,
                I,
                0,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
