package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(ChangeDailyPay.FUNCTION)
object ChangeDailyPayHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = ChangeDailyPay.fromRpc(rfc)

        context.computerHandler.addData(
            ApplicationData(
                ChangeDailyPay.FUNCTION,
                arrayOf<Any?>(parsedCall.change, parsedCall.attackPort),
                parsedCall.port,
                context.crypt(parsedCall.finalizeIP)
            ),
            parsedCall.ip,
            ApplicationData.OUTSIDE
        )
    }
}
