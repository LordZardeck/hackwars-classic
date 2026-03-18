package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("installwatch")
object InstallwatchHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            InstallWatch.fromRpc(
                rfc
            )
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val path =
            parsedCall.path
        val name =
            parsedCall.name
        val type =
            parsedCall.type
        val port =
            parsedCall.port
        val Parameter: Array<Any?>? =
            arrayOf<Any?>(
                path,
                name,
                type
            )
        context.computerHandler.addData(
            ApplicationData(
                "installwatch",
                Parameter,
                port,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
