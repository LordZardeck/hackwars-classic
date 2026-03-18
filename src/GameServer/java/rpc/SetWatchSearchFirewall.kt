package rpc

import assignments.RemoteFunctionCall

data class SetWatchSearchFirewall(val ip: String, val watchID: Int?, val searchFireWall: Int?) :
    RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "setwatchsearchfirewall"
        fun fromRpc(rfc: RemoteFunctionCall): SetWatchSearchFirewall {
            return SetWatchSearchFirewall(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<Int?>(rfc, 1),
                getPositionalParameter<Int?>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, watchID, searchFireWall))
}
