package com.hackwars.rpc

import assignments.RemoteFunctionCall
import game.HackerFile

data class SaveFile(val ip: String, val path: String?, val name: HackerFile?) : RemoteFunctionCallImpl() {
    companion object {
        fun fromRpc(rfc: RemoteFunctionCall): SaveFile {
            return SaveFile(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<HackerFile?>(rfc, 2),
            )
        }
    }

    override val spec = GameFunctions.SAVEFILE
    override fun toRfc(requestId: Int) = RemoteFunctionCall(requestId, spec.wireName, arrayOf<Any?>(ip, path, name))
}
