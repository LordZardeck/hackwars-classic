package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("submit")
object SubmitHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val submitCall =
            Submit.fromRpc(rfc)
        val target_ip =
            submitCall.targetIp
        var source_ip =
            submitCall.sourceIp
        val parameters =
            HashMap(submitCall.parameters)

        parameters["packetid"] =
            rfc.id

        if (!(source_ip == "062.153.7.142"))  //This is the IP used to hook-in and make requests externally.
            source_ip = context.crypt(
                source_ip)

        context.computerHandler.addData(
            ApplicationData(
                "submit",
                parameters,
                0,
                source_ip
            ),
            target_ip,
            ApplicationData.OUTSIDE
        )
    }
}
