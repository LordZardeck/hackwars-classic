package rpc

import assignments.RemoteFunctionCall
import game.HackerFile

data class CompileFile(val ip: String, val path: String?, val name: HackerFile?, val price: Float?) :
    RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "compilefile"
        fun fromRpc(rfc: RemoteFunctionCall): CompileFile {
            return CompileFile(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<HackerFile?>(rfc, 2),
                getPositionalParameter<Float?>(rfc, 3),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, path, name, price))
}
