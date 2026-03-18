package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("makebounty")
object MakeBountyHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            MakeBounty.fromRpc(rfc)
        var source_ip =
            parsedCall.sourceIp
        source_ip = context.crypt(
            source_ip)
        val anonymous =
            parsedCall.anonymous
        val target =
            parsedCall.target
        val type =
            parsedCall.type
        val fname =
            parsedCall.fname
        val folder =
            parsedCall.folder
        val iterations =
            parsedCall.iterations
        val reward =
            parsedCall.reward
        val O: Array<Any?>? =
            arrayOf<Any?>(
                anonymous,
                target,
                type,
                fname,
                folder,
                iterations,
                reward
            )
        context.computerHandler.addData(
            ApplicationData(
                "makebounty",
                O,
                0,
                source_ip
            ),
            source_ip,
            ApplicationData.OUTSIDE
        )
    }
}
