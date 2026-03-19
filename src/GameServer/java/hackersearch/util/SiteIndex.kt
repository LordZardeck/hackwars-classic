package hackersearch.util

import java.io.Serializable

class SiteIndex : Serializable {
    private var rank = 0.0
    private var address: String? = null

    fun setRank(rank: Double) {
        this.rank = rank
    }

    fun getRank(): Double {
        return rank
    }

    fun setAddress(address: String?) {
        this.address = address
    }

    fun getAddress(): String {
        return unsafeValue(address)
    }

    fun clone(): SiteIndex {
        val returnMe = SiteIndex()
        returnMe.setRank(rank)
        returnMe.setAddress(address)
        return returnMe
    }

    private fun <T> unsafeValue(value: Any?): T {
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}
