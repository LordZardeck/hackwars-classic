package rpc

import assignments.RemoteFunctionCall

data class HacktendoTarget(
    val targetX: Int,
    val targetY: Int,
    val ip: String?,
    val currentX: Int,
    val currentY: Int
) : RemoteFunctionCallImpl() {
    companion object {
        const val FUNCTION = "hacktendoTarget"
        fun fromRpc(rfc: RemoteFunctionCall): HacktendoTarget {
            return HacktendoTarget(
                getPositionalParameter<Int>(rfc, 0),
                getPositionalParameter<Int>(rfc, 1),
                getPositionalParameter<String?>(rfc, 2),
                getPositionalParameter<Int>(rfc, 3),
                getPositionalParameter<Int>(rfc, 4),
            )
        }
    }

    override val function = FUNCTION
    override fun toRfc() = RemoteFunctionCall(0, FUNCTION, arrayOf<Any?>(targetX, targetY, ip, currentX, currentY))
}
