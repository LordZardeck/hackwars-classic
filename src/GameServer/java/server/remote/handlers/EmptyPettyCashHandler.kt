package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.EmptyPettyCashPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(EmptyPettyCash.FUNCTION)
object EmptyPettyCashHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = EmptyPettyCash.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        val targetIP = parsedCall.targetIP
        val targetPort = parsedCall.targetPort
        context.computerHandler.addData(
            ApplicationData(EmptyPettyCashPayload(parsedCall.windowHandle), targetPort, ip),
            targetIP,
            ApplicationData.OUTSIDE
        )
    }
}
