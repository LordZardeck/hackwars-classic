package hackersearch.util

import java.io.Serializable

class WordIndex(Word: String?, Category: String?) : Serializable {
    private var Word: String? = Word
    private var Category: String? = Category
    private var lastAccessed = 0L
    private var SBL: SiteBinaryList = SiteBinaryList()
    private var RBL: RankBinaryList = RankBinaryList()

    fun setLastAccessed(lastAccessed: Long) {
        this.lastAccessed = lastAccessed
    }

    fun getLastAccessed(): Long {
        return lastAccessed
    }

    fun getWord(): String {
        return unsafeValue(Word)
    }

    fun getCategory(): String {
        return unsafeValue(Category)
    }

    fun addSite(SI: SiteIndex?) {
        SBL.add(SI)
        RBL.add(SI)
    }

    fun removeSite(SI: SiteIndex) {
        SBL.remove(SI.getAddress())
        RBL.remove(java.lang.Double(SI.getRank()))
    }

    fun getSiteBinaryList(): SiteBinaryList {
        return SBL
    }

    fun getRankBinaryList(): RankBinaryList {
        return RBL
    }

    private fun <T> unsafeValue(value: Any?): T {
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}
