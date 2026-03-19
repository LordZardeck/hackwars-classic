package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(SetWatchNote.FUNCTION)
object SetWatchNoteHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall =
            SetWatchNote.fromRpc(
                rfc
            )
        var ip =
            parsedCall.ip
        ip = context.crypt(
            ip)
        val watchID =
            parsedCall.watchID
        val note =
            parsedCall.note
        val O: Any =
            arrayOf<Any?>(
                watchID,
                note
            )
        context.computerHandler.addData(
            ApplicationData(
                SetWatchNote.FUNCTION,
                O,
                0,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
