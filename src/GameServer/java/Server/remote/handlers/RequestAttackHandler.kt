package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("requestattack")
object RequestAttackHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestAttack.fromRpc(rfc)
        val targetIP = parsedCall.targetIP
        val targetPort = parsedCall.targetPort
        var sourceIP = parsedCall.sourceIP
        sourceIP = context.crypt(sourceIP)

        val sourcePort = parsedCall.sourcePort

        val secondaryPorts = parsedCall.secondaryPorts
        val scripts = parsedCall.scripts
        val extraInfo = parsedCall.extraInfo
        val windowHandle = parsedCall.windowHandle

        val Parameters: Array<Any?>? =
            arrayOf<Any?>(targetIP, targetPort, secondaryPorts, scripts, extraInfo, windowHandle)
        val AD = ApplicationData("requestattack", Parameters, sourcePort, sourceIP)

        if (targetIP != sourceIP && targetIP.indexOf("store") == -1) context.computerHandler.addData(
            AD,
            sourceIP,
            ApplicationData.OUTSIDE
        )
    }
}
