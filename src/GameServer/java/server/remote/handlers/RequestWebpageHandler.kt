package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(RequestWebpage.FUNCTION)
object RequestWebpageHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val requestWebpageCall = RequestWebpage.fromRpc(rfc)
        var targetIp = requestWebpageCall.targetIp
        var sourceIp = requestWebpageCall.sourceIp

        if (sourceIp != "062.153.7.142")  //This is the IP used to hook-in and make requests externally.
            sourceIp = context.crypt(sourceIp)

        val parameters = HashMap(requestWebpageCall.parameters)
        parameters["packetid"] = rfc.id

        if (targetIp.length >= 5 && targetIp.substring(0, 5).lowercase(Locale.getDefault()) == "store")
            targetIp = "store" + context.serverID

        context.computerHandler.addData(
            ApplicationData(RequestWebpage.FUNCTION, parameters, 0, sourceIp),
            targetIp,
            ApplicationData.OUTSIDE
        )
    }
}
