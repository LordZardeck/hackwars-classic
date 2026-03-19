package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(UninstallPort.FUNCTION)
object UninstallPortHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            UninstallPort.fromRpc(
                rfc
            )
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val port =
            parsedCall.port
        context.computerHandler.addData(
            ApplicationData(
                UninstallPort.FUNCTION,
                port,
                port,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
