package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RequestSecondaryDirectory.FUNCTION)
object RequestSecondaryDirectoryHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            RequestSecondaryDirectory.fromRpc(
                rfc
            )
        val ip =
            parsedCall.ip
        val path =
            parsedCall.path
        var targetIP =
            parsedCall.targetIP

        targetIP =
            context.crypt(
                targetIP)

        val port =
            parsedCall.port
        val Parameter: Array<Any?>? =
            arrayOf<Any?>(
                targetIP,
                path,
                rfc.getID()
            )
        context.computerHandler.addData(
            ApplicationData(
                RequestSecondaryDirectory.FUNCTION,
                Parameter,
                port,
                targetIP
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
