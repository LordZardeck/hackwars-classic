package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.CombatRequestScanPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RequestScan.FUNCTION)
object RequestScanHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestScan.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        val targetIP = parsedCall.targetIP
        if (ip != targetIP) context.computerHandler.addData(
            ApplicationData(CombatRequestScanPayload(ip), 0, ip),
            targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
