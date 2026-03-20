package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationCommand
import game.ApplicationData
import com.hackwars.rpc.*
import game.payload.StringCommandPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(SavePortNote.FUNCTION)
object SavePortNoteHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = SavePortNote.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(
                StringCommandPayload(ApplicationCommand.of(SavePortNote.FUNCTION), parsedCall.note),
                parsedCall.port,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
