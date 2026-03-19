package hackersearch.util

import com.plink.dolphinnet.util.BinaryList
import java.io.Serializable

class RankBinaryList : BinaryList(), Serializable {
    override fun getKey(o: Any?): Any? {
        val temp = o as SiteIndex
        return java.lang.Double(temp.getRank())
    }

    override fun compare(o1: Any?, o2: Any?): Int {
        val s1 = -(o1 as Double)
        val s2 = -(o2 as Double)
        return s1.compareTo(s2)
    }

    override fun toString(): String {
        var returnMe = ""
        for (i in 0 until getData().size) {
            val SI = getData()[i] as SiteIndex
            returnMe += SI.getAddress() + "->" + SI.getRank() + "\n"
        }
        return returnMe
    }
}
