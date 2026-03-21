package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.RequestZombieAttack
import game.ApplicationData
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.REQUESTZOMBIEATTACK)
object RequestZombieAttackHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestZombieAttack.fromRpc(rfc)
        val targetIP = parsedCall.targetIP
        val sourceIP = parsedCall.sourceIP
        val parentIP = context.crypt(parsedCall.parentIP)
        val applicationData = ApplicationData(parsedCall.copy(parentIP = parentIP), parsedCall.sourcePort, parentIP)

        if (targetIP != sourceIP && targetIP.indexOf(com.hackwars.rpc.GameCommandWires.REQUESTZOMBIEATTACK) == -1) context.computerHandler.addData(
            applicationData,
            parentIP,
            ApplicationData.OUTSIDE
        )
    }
}
