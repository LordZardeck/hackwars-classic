package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.Submit
import game.ApplicationData
import game.payload.SubmitPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.SUBMIT)
object SubmitHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val submitCall = Submit.fromRpc(rfc)
        val parameters = HashMap(submitCall.parameters)
        parameters["packetid"] = rfc.id
        var source_ip = submitCall.sourceIp

        if (!(source_ip == "062.153.7.142"))  //This is the IP used to hook-in and make requests externally.
            source_ip = context.crypt(
                source_ip
            )

        context.computerHandler.addData(
            ApplicationData(SubmitPayload(parameters), 0, source_ip),
            submitCall.targetIp,
            ApplicationData.OUTSIDE
        )
    }
}
