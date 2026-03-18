package rpc

import assignments.RemoteFunctionCall

data class RequestTask(val fileName: String?, val questID: String?, val taskName: String?, val targetIP: String?) :
    RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "requesttask"
        fun fromRpc(rfc: RemoteFunctionCall): RequestTask {
            return RequestTask(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<String?>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<String?>(rfc, 3),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(fileName, questID, taskName, targetIP))
}
