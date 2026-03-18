package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("requestdirectory")
object RequestdirectoryHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            RequestDirectory.fromRpc(
                rfc
            )
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val path =
            parsedCall.path
        val O: Array<Any?>? =
            arrayOf<Any?>(
                path,
                rfc.getID()
            )
        context.computerHandler.addData(
            ApplicationData(
                "requestdirectory",
                O,
                0,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
