package hackersearch.util

import com.plink.dolphinnet.util.BinaryList
import com.plink.dolphinstem.WordBinaryList
import com.plink.dolphinstem.WordData
import java.io.Serializable

class WordBinaryListByRank : BinaryList(), Serializable {
    override fun getKey(o: Any?): Any? {
        val temp = o as WordData
        return java.lang.Double(temp.getFrequency())
    }

    override fun compare(o1: Any?, o2: Any?): Int {
        val s1 = o1 as Double
        val s2 = o2 as Double
        return s2.compareTo(s1)
    }

    override fun toString(): String {
        var returnMe = ""
        val data = getData()
        for (i in 0 until data.size) {
            val temp = data[i] as WordData
            returnMe += "> " + temp.getData() + " x " + temp.getFrequency() + "\n"
        }
        return returnMe
    }

    fun addList(L: WordBinaryList) {
        for (i in 0 until L.getData().size) {
            val A = L.getData()[i] as WordData
            val B = this.get(A.getData()) as WordData?
            if (B != null) {
                B.setFrequency(B.getFrequency() + 1.0)
            } else {
                this.add(WordData(A.getData(), 1.0))
            }
        }
    }

    fun clone(): WordBinaryList {
        val Clone = WordBinaryList()
        for (i in 0 until this.getData().size) {
            val temp = this.getData()[i] as WordData
            Clone.add(temp.clone())
        }
        return Clone
    }

    fun zero() {
        for (i in 0 until getData().size) {
            val temp = getData()[i] as WordData
            temp.setFrequency(0.0)
        }
    }
}
