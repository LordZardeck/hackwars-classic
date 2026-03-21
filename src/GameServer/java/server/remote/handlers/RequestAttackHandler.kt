package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.RequestAttack
import game.ApplicationData
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.REQUESTATTACK)
object RequestAttackHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestAttack.fromRpc(rfc)
        val targetIP = parsedCall.targetIP
        val sourceIP = context.crypt(parsedCall.sourceIP)
        val applicationData = ApplicationData(parsedCall.copy(sourceIP = sourceIP), parsedCall.sourcePort, sourceIP)

        if (targetIP != sourceIP && targetIP.indexOf(com.hackwars.rpc.GameCommandWires.REQUESTATTACK) == -1) context.computerHandler.addData(
            applicationData,
            sourceIP,
            ApplicationData.OUTSIDE
        )
    }
}
