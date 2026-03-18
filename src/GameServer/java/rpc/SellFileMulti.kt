package rpc

import assignments.RemoteFunctionCall

@Suppress("ArrayInDataClass")
data class SellFileMulti(val ip: String, val allFiles: Array<Any?>?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "sellfilemulti"
        fun fromRpc(rfc: RemoteFunctionCall): SellFileMulti {
            return SellFileMulti(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Array<Any?>?>(rfc, 1),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, allFiles))
}
