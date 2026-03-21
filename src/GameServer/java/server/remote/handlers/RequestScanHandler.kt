package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.RequestScan
import game.ApplicationData
import game.payload.CombatRequestScanPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.REQUESTSCAN)
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
