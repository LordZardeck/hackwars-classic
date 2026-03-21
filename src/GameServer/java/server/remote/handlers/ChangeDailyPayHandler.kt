package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.ChangeDailyPay
import game.ApplicationData
import game.payload.ChangeDailyPayPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.CHANGEDAILYPAY)
object ChangeDailyPayHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = ChangeDailyPay.fromRpc(rfc)

        context.computerHandler.addData(
            ApplicationData(
                ChangeDailyPayPayload(
                    targetIp = parsedCall.change?.let(context::crypt).orEmpty(),
                    windowHandle = parsedCall.attackPort
                ),
                parsedCall.port,
                context.crypt(parsedCall.finalizeIP)
            ),
            parsedCall.ip,
            ApplicationData.OUTSIDE
        )
    }
}
