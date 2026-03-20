package hackersearch.server

import assignments.*
import com.plink.dolphinnet.Assignment
import hackersearch.assignments.IndexPageAssignment
import hackersearch.assignments.SearchAssignment
import hackersearch.assignments.SearchResultAssignment
import hackersearch.util.SearchHandler
import server.runtime.GameServerRuntime
import java.util.HashMap

class SearchServer @JvmOverloads constructor(fake: Boolean = false) {
    private var RESULT = false
    private var Result: Assignment? = null
    private var Results = HashMap<Any?, Any?>()
    private val runtime = GameServerRuntime()

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
        return handler().requestSearch(MyAssignment as SearchAssignment)
    }

    fun returnAssignment(MyAssignment: Assignment) {
        if (MyAssignment is IndexPageAssignment) {
            val IPA = MyAssignment
            handler().indexPage(IPA.getTitle(), IPA.getAddress(), IPA.getContent())
        }
    }

    fun getSearchHandlerForTesting(): SearchHandler? {
        return MySearchHandler
    }

    fun setSearchHandlerForTesting(handler: SearchHandler?) {
        MySearchHandler = handler
    }

    fun shutdown() {
        MySearchHandler?.shutdown()
        runtime.close()
        if (MySearchServer === this) {
            MySearchServer = null
        }
    }

    private fun handler(): SearchHandler {
        MySearchHandler?.let { return it }

        if (MySearchServer == null) {
            MySearchServer = this
        }

        val handler = SearchHandler.getInstance(MySearchServer, runtime)
        MySearchHandler = handler
        return handler
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
            }

            if (MySearchHandler == null) {
                MySearchHandler = SearchHandler.getInstance(MySearchServer)
            }

            return MySearchServer!!
        }

        @JvmStatic
        fun resetForTests() {
            MySearchServer = null
            MySearchHandler = null
            SearchHandler.resetForTests()
        }

        @JvmStatic
        fun installSearchHandlerForTesting(handler: SearchHandler?) {
            MySearchHandler = handler
        }
    }
}
