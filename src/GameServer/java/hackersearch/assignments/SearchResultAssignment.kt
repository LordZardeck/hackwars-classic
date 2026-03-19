package hackersearch.assignments

import com.plink.dolphinnet.Assignment
import com.plink.dolphinnet.DataHandler
import java.io.Serializable
import java.util.ArrayList

class SearchResultAssignment(id: Int) : Assignment(id), Serializable {
    private val searchResults = ArrayList<Any?>()
    private val searchTerms = ArrayList<Any?>()
    private var size = 0
    private var current = 0
    private var commentCount = 0
    private var ranking = 0.0

    fun setSize(size: Int) {
        this.size = size
    }

    fun getSize(): Int {
        return size
    }

    fun setCommentCount(commentCount: Int) {
        this.commentCount = commentCount
    }

    fun getCommentCount(): Int {
        return commentCount
    }

    fun getRanking(): Double {
        return ranking
    }

    fun setRanking(ranking: Double) {
        this.ranking = ranking
    }

    fun setCurrent(current: Int) {
        this.current = current
    }

    fun getCurrent(): Int {
        return current
    }

    fun addResult(SR: SearchResult?) {
        searchResults.add(SR)
    }

    fun addSearchTerm(Term: String?) {
        searchTerms.add(Term)
    }

    fun getSearchTerms(): ArrayList<Any?> {
        return searchTerms
    }

    fun getResults(): ArrayList<Any?> {
        return searchResults
    }

    override fun execute(DH: DataHandler): Any? {
        DH.addData(this)
        finish()
        return null
    }
}
