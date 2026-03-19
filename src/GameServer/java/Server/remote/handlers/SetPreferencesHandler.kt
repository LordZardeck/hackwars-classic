package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import com.hackwars.rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler(SetPreferences.FUNCTION)
object SetPreferencesHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = SetPreferences.fromRpc(rfc)
        var ip = parsedCall.ip
        ip = context.crypt(ip)
        val preferences =
            parsedCall.preferences
        val O = arrayOf<Any?>(ip, preferences)
        context.computerHandler.addData(
            ApplicationData(SetPreferences.FUNCTION, O, 0, ip),
            ip,
            ApplicationData.OUTSIDE
        )
    }
}
