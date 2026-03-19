package hackersearch.util

import com.plink.dolphinnet.util.BinaryList
import java.io.Serializable

class InverseLookup : BinaryList(), Serializable {
    override fun getKey(o: Any?): Any? {
        val temp = o as WordIndex
        return temp.getWord()
    }

    override fun compare(o1: Any?, o2: Any?): Int {
        val s1 = o1 as String
        val s2 = o2 as String
        return s1.compareTo(s2)
    }

    override fun toString(): String {
        var returnMe = ""
        for (i in 0 until this.getData().size) {
            val WI = this.getData()[i] as WordIndex
            returnMe += WI.getWord() + "\n"
        }
        return returnMe
    }
}
