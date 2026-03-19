package hackersearch.util

import com.plink.dolphinnet.util.BinaryList
import java.io.Serializable

class SiteDataBinaryList : BinaryList(), Serializable {
    override fun getKey(o: Any?): Any? {
        val temp = o as SiteData
        return temp.getAddress()
    }

    override fun compare(o1: Any?, o2: Any?): Int {
        val s1 = o1 as String
        val s2 = o2 as String
        return s1.compareTo(s2)
    }

    override fun toString(): String = nullValue()

    private fun <T> nullValue(): T {
        @Suppress("UNCHECKED_CAST")
        return null as T
    }
}
