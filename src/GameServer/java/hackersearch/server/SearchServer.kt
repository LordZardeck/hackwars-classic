package hackersearch.server

import assignments.*
import com.plink.dolphinnet.Assignment
import hackersearch.assignments.IndexPageAssignment
import hackersearch.assignments.SearchAssignment
import hackersearch.assignments.SearchResultAssignment
import hackersearch.util.SearchHandler
import java.util.HashMap

class SearchServer @JvmOverloads constructor(fake: Boolean = false) {
    private var RESULT = false
    private var Result: Assignment? = null
    private var Results = HashMap<Any?, Any?>()

    fun hasResult(STAMP: Int): Boolean {
        if (Results[java.lang.Integer(STAMP)] != null) {
            return true
        }
        return false
    }

    fun getResult(STAMP: Int): Assignment? {
        return Results[java.lang.Integer(STAMP)] as Assignment?
    }

    fun dispatchPacket(DispatchMe: Assignment, connectionID: Int) {
        Results[java.lang.Integer(DispatchMe.getID())] = DispatchMe
    }

    fun requestSearch(MyAssignment: Assignment): SearchResultAssignment {
        return MySearchHandler!!.requestSearch(MyAssignment as SearchAssignment)
    }

    fun returnAssignment(MyAssignment: Assignment) {
        if (MyAssignment is IndexPageAssignment) {
            val IPA = MyAssignment
            MySearchHandler!!.indexPage(IPA.getTitle(), IPA.getAddress(), IPA.getContent())
        }
    }

    companion object {
        @JvmField
        var MySearchServer: SearchServer? = null

        @JvmField
        var MySearchHandler: SearchHandler? = null

        @JvmStatic
        fun getInstance(): SearchServer {
            if (MySearchServer == null) {
                MySearchServer = SearchServer(false)
                MySearchHandler = SearchHandler.getInstance(MySearchServer)
            }

            return MySearchServer!!
        }
    }
}
