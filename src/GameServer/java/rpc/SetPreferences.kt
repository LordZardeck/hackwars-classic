package rpc

import assignments.RemoteFunctionCall

data class SetPreferences(val ip: String, val preferences: HashMap<*, *>?) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "setpreferences"
        fun fromRpc(rfc: RemoteFunctionCall): SetPreferences {
            return SetPreferences(
                getPositionalParameter<String>(rfc, 0),
                getPositionalParameter<HashMap<*, *>?>(rfc, 1),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, preferences))
}
