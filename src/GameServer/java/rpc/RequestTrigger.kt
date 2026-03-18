package rpc

import assignments.RemoteFunctionCall

data class RequestTrigger(
    val watchNote: String?,
    val triggerParam: HashMap<*, *>?,
    val sourceIP: String?,
    val targetIP: String?
) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "requesttrigger"
        fun fromRpc(rfc: RemoteFunctionCall): RequestTrigger {
            return RequestTrigger(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<HashMap<*, *>?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<String?>(rfc, 3),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(watchNote, triggerParam, sourceIP, targetIP))
}
