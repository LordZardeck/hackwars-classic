package rpc

import assignments.RemoteFunctionCall
import game.HackerFile

data class SaveFile(val ip: String, val path: String?, val name: HackerFile?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "savefile"
        fun fromRpc(rfc: RemoteFunctionCall): SaveFile {
            return SaveFile(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<HackerFile?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, path, name))
}
