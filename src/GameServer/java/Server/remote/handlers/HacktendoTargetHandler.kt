package server.remote.handlers

import assignments.RemoteFunctionCall
import game.ApplicationData
import rpc.*
import server.remote.RemoteCallContext
import server.remote.RemoteCallHandler
import server.remote.RpcHandler
import java.util.*

@RpcHandler("hacktendoTarget")
object HacktendoTargetHandler : RemoteCallHandler {
    override fun handle(rfc: RemoteFunctionCall, context: RemoteCallContext) {
        val parsedCall = HacktendoTarget.fromRpc(rfc)
        val targetX = parsedCall.targetX
        val targetY = parsedCall.targetY
        val ip = parsedCall.ip
        val currentX = parsedCall.currentX
        val currentY = parsedCall.currentY

        val O = arrayOf<Any>(targetX, targetY, currentX, currentY)
        context.computerHandler.addData(
            ApplicationData(
                "hacktendoTarget",
                O,
                0,
                ip
            ), ip, ApplicationData.INSIDE
        )
    }
}
