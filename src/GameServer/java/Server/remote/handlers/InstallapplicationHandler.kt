package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(InstallApplication.FUNCTION)
object InstallapplicationHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            InstallApplication.fromRpc(
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
                InstallApplication.FUNCTION,
                Parameter,
                port,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
