package hackersearch.assignments

import com.plink.dolphinnet.Assignment
import com.plink.dolphinnet.DataHandler
import com.plink.dolphinstem.ItemData
import com.plink.dolphinstem.TextSource
import com.plink.dolphinstem.WordBinaryList
import hackersearch.util.SearchHandler
import java.util.ArrayList

class SearchAssignment(id: Int) : Assignment(id) {
    private var index = 0
    private var querySet = false
    private var query = ""

    fun setIndex(index: Int) {
        this.index = index
    }

    fun getIndex(): Int {
        return index
    }

    fun getQuerySet(): Boolean {
        return querySet
    }

    fun setVector(query: String?) {
        this.query = query ?: ""
    }

    fun getVector(): WordBinaryList? {
        var titleVector: WordBinaryList? = null
        val primeMe = ArrayList<Any?>()
        primeMe.add(query)
        val prime = TextSource("source", primeMe)
        prime.setStopWordFile(SearchHandler.STOP_WORD_LOCATION)
        prime.prime()
        val temp = prime.getItems()
        if (temp != null) {
            if (temp.size > 0) {
                titleVector = (temp[0] as ItemData).getVectorData()
            }
        }
        return titleVector
    }

    override fun execute(DH: DataHandler): Any? {
        DH.addData(this)
        finish()
        return null
    }
}
