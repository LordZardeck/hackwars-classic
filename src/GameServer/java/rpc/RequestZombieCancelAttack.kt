package rpc

import assignments.RemoteFunctionCall

data class RequestZombieCancelAttack(val ip: String?, val port: Int, val targetIP: String) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "requestzombiecancelattack"
        fun fromRpc(rfc: RemoteFunctionCall): RequestZombieCancelAttack {
            return RequestZombieCancelAttack(
                getPositionalParameter<String?>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<String>(rfc, 2),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(ip, port, targetIP))
}
