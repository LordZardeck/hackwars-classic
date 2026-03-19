package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(SetWatchSearchFirewall.FUNCTION)
object SetWatchSearchFirewallHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            SetWatchSearchFirewall.fromRpc(
                rfc
            )
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val watchID =
            parsedCall.watchID
        val searchFireWall =
            parsedCall.searchFireWall
        val O: Any =
            arrayOf<Any?>(
                watchID,
                searchFireWall
            )
        context.computerHandler.addData(
            ApplicationData(
                SetWatchSearchFirewall.FUNCTION,
                O,
                0,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
