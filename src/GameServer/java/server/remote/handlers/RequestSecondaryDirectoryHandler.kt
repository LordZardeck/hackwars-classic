package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.RequestSecondaryDirectoryPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RequestSecondaryDirectory.FUNCTION)
object RequestSecondaryDirectoryHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = RequestSecondaryDirectory.fromRpc(rfc)
        val targetIP = context.crypt(parsedCall.targetIP)
        context.computerHandler.addData(
            ApplicationData(
                RequestSecondaryDirectoryPayload(targetIP, parsedCall.path.orEmpty(), rfc.getID()),
                parsedCall.port,
                targetIP
            ),
            parsedCall.ip,
            ApplicationData.OUTSIDE
        )
    }
}
