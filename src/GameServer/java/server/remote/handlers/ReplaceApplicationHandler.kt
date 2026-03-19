package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(ReplaceApplication.FUNCTION)
object ReplaceApplicationHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            ReplaceApplication.fromRpc(
                rfc
            )
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val port =
            parsedCall.port
        val path =
            parsedCall.path
        val name =
            parsedCall.name
        val Parameter: Array<String?>? =
            arrayOf<String?>(
                path,
                name
            )
        context.computerHandler.addData(
            ApplicationData(
                ReplaceApplication.FUNCTION,
                Parameter,
                port,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
