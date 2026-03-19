package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RequestZombieAttack.FUNCTION)
object RequestZombieAttackHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestZombieAttack.fromRpc(rfc)
        val targetIP = parsedCall.targetIP
        val targetPort = parsedCall.targetPort
        val sourceIP = parsedCall.sourceIP
        val sourcePort = parsedCall.sourcePort

        val I = parsedCall.I
        val S = parsedCall.S
        val O = parsedCall.O
        var parentIP = parsedCall.parentIP
        parentIP = context.crypt(parentIP)

        val Parameters: Array<Any?>? = arrayOf<Any?>(targetIP, targetPort, I, S, O, sourceIP)
        val AD = ApplicationData(RequestZombieAttack.FUNCTION, Parameters, sourcePort, parentIP)

        if (targetIP != sourceIP && targetIP.indexOf(RequestZombieAttack.FUNCTION) == -1) context.computerHandler.addData(
            AD,
            parentIP,
            ApplicationData.OUTSIDE
        )
    }
}
