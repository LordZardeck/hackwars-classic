package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(SetFtpPassword.FUNCTION)
object SetFtpPasswordHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            SetFtpPassword.fromRpc(
                rfc
            )
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val password =
            parsedCall.password
        context.computerHandler.addData(
            ApplicationData(
                SetFtpPassword.FUNCTION,
                password,
                0,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
