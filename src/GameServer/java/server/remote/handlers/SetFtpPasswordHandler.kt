package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.SetFtpPassword
import game.ApplicationCommand
import game.ApplicationData
import game.payload.StringCommandPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.SETFTPPASSWORD)
object SetFtpPasswordHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = SetFtpPassword.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(
                StringCommandPayload(
                    ApplicationCommand.of(com.hackwars.rpc.GameCommandWires.SETFTPPASSWORD),
                    parsedCall.password
                ), 0, ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
