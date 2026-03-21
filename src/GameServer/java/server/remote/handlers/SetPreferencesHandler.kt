package server.remote.handlers

import assignments.RemoteFunctionCall
import com.hackwars.rpc.SetPreferences
import game.ApplicationData
import game.payload.SetPreferencesPayload
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler

@RpcHandler(com.hackwars.rpc.GameCommandWires.SETPREFERENCES)
object SetPreferencesHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = SetPreferences.fromRpc(rfc)
        val ip = context.crypt(parsedCall.ip)
        context.computerHandler.addData(
            ApplicationData(
                SetPreferencesPayload(HashMap(parsedCall.preferences ?: emptyMap<Any?, Any?>())),
                0,
                ip
            ),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
