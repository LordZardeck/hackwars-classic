package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RequestScan.FUNCTION)
object RequestScanHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestScan.fromRpc(rfc)
        var ip = parsedCall.ip
        ip = context.crypt(ip)
        val targetIP = parsedCall.targetIP
        if (ip != targetIP) context.computerHandler.addData(
            ApplicationData(RequestScan.FUNCTION, ip, 0, ip),
            targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
