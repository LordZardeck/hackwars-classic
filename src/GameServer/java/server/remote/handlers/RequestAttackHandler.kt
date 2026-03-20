package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RequestAttack.FUNCTION)
object RequestAttackHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestAttack.fromRpc(rfc)
        val targetIP = parsedCall.targetIP
        val sourceIP = context.crypt(parsedCall.sourceIP)
        val applicationData = ApplicationData(parsedCall.copy(sourceIP = sourceIP), parsedCall.sourcePort, sourceIP)

        if (targetIP != sourceIP && targetIP.indexOf(RequestAttack.FUNCTION) == -1) context.computerHandler.addData(
            applicationData,
            sourceIP,
            ApplicationData.OUTSIDE
        )
    }
}
