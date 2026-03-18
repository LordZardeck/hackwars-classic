package rpc

import assignments.RemoteFunctionCall

data class DecompileFile(val ip: String, val location: String?, val fileName: String?, val compileCost: Float?) :
    RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "decompilefile"
        fun fromRpc(rfc: RemoteFunctionCall): DecompileFile {
            return DecompileFile(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<Float?>(rfc, 3),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, location, fileName, compileCost))
}
