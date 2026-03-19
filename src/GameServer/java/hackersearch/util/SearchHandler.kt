package hackersearch.util

import com.plink.dolphinstem.ItemData
import com.plink.dolphinstem.TextSource
import com.plink.dolphinstem.WordBinaryList
import com.plink.dolphinstem.WordData
import hackersearch.assignments.SearchAssignment
import hackersearch.assignments.SearchResult
import hackersearch.assignments.SearchResultAssignment
import hackersearch.server.SearchServer
import org.w3c.dom.Node
import util.LoadXML
import util.Time
import util.sql
import java.util.ArrayList
import java.util.Iterator
import java.util.concurrent.Semaphore

open class SearchHandler(MyServer: SearchServer?) : Runnable {
    private var MyTime: Time? = null
    private var ExecuteStack = ArrayList<Any?>()
    private var MyThread: Thread? = null
    private var MyServer: SearchServer? = null
    private val available = Semaphore(1, true)
    private var MyInverseLookup = InverseLookup()
    private var DocumentFrequency = WordBinaryList()
    private var SDBL = SiteDataBinaryList()
    private var currentlyIndexed = 0
    private var fileCount = 0
    private var fileList: Array<String?>? = null
    private var count = 0
    private var Connection = "127.0.0.1"
    private var DB = "hackwars"
    private var Username = "root"
    private var Password = ""

    init {
        this.MyTime = Time.getInstance()
        this.MyServer = MyServer

        try {
            fileCount = 0
            val C = sql(Connection, DB, Username, Password)
            var result: ArrayList<Any?>? = null
            val Q = "select max(num) from user;"
            result = C.process(Q)
            if (result != null) {
                fileCount = Integer.parseInt(result[0] as String)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        MyThread = Thread(this)
        MyThread!!.start()
    }

    @Suppress("unused")
    inner class requestSearchTask(private var MySearchAssignment: SearchAssignment?) : Task {
        private var EliminateDuplicate = WordBinaryList()

        override fun execute() {
        }

        fun executeBlocking(): SearchResultAssignment {
            var result: SearchResultAssignment? = null
            val SearchTerms = MySearchAssignment!!.getVector()
            if (SearchTerms != null) {
                val Results = arrayOfNulls<WordIndex>(SearchTerms.getData().size)
                for (i in 0 until SearchTerms.getData().size) {
                    Results[i] = MyInverseLookup.get(((SearchTerms.getData()[i]) as WordData).getData()) as WordIndex?
                }

                result = calculatePageRank(Results, SearchTerms)
                if (result == null) {
                    result = SearchResultAssignment(0)
                }
            }

            val Results = result!!.getResults()
            for (i in 0 until Results.size) {
                val SR = Results[i] as SearchResult
            }

            return result!!
        }

        private fun calculatePageRank(Results: Array<WordIndex?>?, SearchTerms: WordBinaryList): SearchResultAssignment {
            val returnMe = SearchResultAssignment(MySearchAssignment!!.getID())
            for (i in 0 until SearchTerms.getData().size) {
                returnMe.addSearchTerm(((SearchTerms.getData()[i]) as WordData).getData())
            }
            val SearchResults = RankBinaryList()

            if (Results != null) {
                if (Results.isEmpty()) {
                    return returnMe
                }

                val Counters = IntArray(Results.size)
                val Sites = arrayOfNulls<SiteIndex>(Results.size)

                var stop = false
                while (!stop) {
                    for (i in Results.indices) {
                        if (Results[i] != null) {
                            Sites[i] = if (Counters[i] < Results[i]!!.getSiteBinaryList().getData().size) {
                                Results[i]!!.getSiteBinaryList().getData()[Counters[i]] as SiteIndex
                            } else {
                                null
                            }
                        } else {
                            Sites[i] = null
                        }
                    }

                    var Minimum = Sites[0]
                    var minimumIndex = 0
                    for (i in 1 until Sites.size) {
                        if (Minimum == null) {
                            Minimum = Sites[i]
                            minimumIndex = i
                        } else if (Sites[i] != null) {
                            if (Sites[i]!!.getAddress().compareTo(Minimum.getAddress()) < 0) {
                                Minimum = Sites[i]
                                minimumIndex = i
                            }
                        }
                    }

                    if (Minimum == null) {
                        break
                    }

                    val AddMe = Minimum.clone()
                    var mcount = 0
                    for (i in Sites.indices) {
                        if (Sites[i] != null) {
                            val temp = Sites[i]
                            if (temp!!.getAddress().equals(Minimum.getAddress())) {
                                if (i != minimumIndex) {
                                    AddMe.setRank(AddMe.getRank() + temp.getRank())
                                }
                                Counters[i]++
                                mcount++
                            }
                        }
                    }
                    if (mcount == Sites.size) {
                        AddMe.setRank(AddMe.getRank() / 3.0)
                        SearchResults.add(AddMe)
                    }

                    stop = true
                    for (i in Results.indices) {
                        if (Results[i] != null) {
                            if (Counters[i] < Results[i]!!.getSiteBinaryList().getData().size) {
                                stop = false
                                break
                            }
                        }
                    }
                }
            }

            var result: ArrayList<Any?>? = null
            if (SearchResults.getData().size - MySearchAssignment!!.getIndex() > 0) {
                val upperBound = Math.min(ReturnCount, SearchResults.getData().size - MySearchAssignment!!.getIndex()) + MySearchAssignment!!.getIndex()
                for (i in MySearchAssignment!!.getIndex() until upperBound) {
                    val SI = SearchResults.getData()[i] as SiteIndex
                    val SD = SDBL.get(SI.getAddress()) as SiteData

                    val title = SD.getTitle()
                    val description = SD.getDescription()
                    val SR = SearchResult()
                    SR.setAddress(SD.getAddress())
                    SR.setTitle(title)
                    SR.setDescription(description)
                    returnMe.addResult(SR)
                }
            }

            returnMe.setCurrent(MySearchAssignment!!.getIndex())
            returnMe.setSize(SearchResults.getData().size)

            return returnMe
        }
    }

    fun requestSearch(MySearchAssignment: SearchAssignment): SearchResultAssignment {
        try {
            available.acquire()
            val RST = requestSearchTask(MySearchAssignment)
            return RST.executeBlocking()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            available.release()
        }
        @Suppress("UNREACHABLE_CODE")
        return null as SearchResultAssignment
    }

    inner class indexPageTask(private var title: String?, private var address: String?, private var content: String?) : Task {
        override fun execute() {
            title = title!!.replace(Regex("\\<.*?\\>"), "")
            title = title!!.replace(Regex("\\&.*?;"), "")
            content = content!!.replace(Regex("\\<.*?\\>"), "")
            content = content!!.replace(Regex("\\&.*?;"), "")

            if (SDBL.get(address) != null) {
                val SD = SDBL.get(address) as SiteData
                val WBL = SD.getTerms()
                for (i in 0 until WBL.getData().size) {
                    val WD = WBL.getData()[i] as WordData
                    val WI = MyInverseLookup.get(WD.getData()) as WordIndex
                    val SBL = WI.getSiteBinaryList()
                    val SI = SBL.get(SD.getAddress()) as SiteIndex
                    WI.removeSite(SI)

                    if (SBL.getData().size == 0) {
                        MyInverseLookup.remove(WD.getData())
                    }

                    val DFWD = DocumentFrequency.get(WD.getData()) as WordData?
                    if (DFWD != null) {
                        DFWD.setFrequency(DFWD.getFrequency() - 1.0)
                        if (DFWD.getFrequency() <= 0.0) {
                            DocumentFrequency.remove(WD.getData())
                        }
                    }
                }
                SDBL.remove(address)
            }

            var WBL: WordBinaryList? = null
            val PrimeMe = ArrayList<Any?>()
            PrimeMe.add(content)
            val Prime = TextSource("source", PrimeMe)
            Prime.setStopWordFile(STOP_WORD_LOCATION)
            Prime.prime()
            val temp = Prime.getItems()

            if (temp != null) {
                if (temp.size > 0) {
                    WBL = (temp[0] as ItemData).getVectorData()
                }
            }

            if (WBL != null) {
                DocumentFrequency.addList(WBL)

                var tCount = 0.0f
                for (i in 0 until WBL.getData().size) {
                    tCount += (WBL.getData()[i] as WordData).getFrequency().toFloat()
                }

                val WBLRanked = WordBinaryListByRank()
                for (i in 0 until WBL.getData().size) {
                    val WD = WBL.getData()[i] as WordData
                    val DF = WD.getFrequency() / tCount
                    val WDIDF = DocumentFrequency.get(WD.getData()) as WordData
                    val IDF = Math.log(WDIDF.getFrequency() * currentlyIndexed)
                    WD.setFrequency(DF * IDF)
                    WBLRanked.add(WD)
                }

                WBL = WordBinaryList()
                for (i in 0 until WBLRanked.getData().size) {
                    WBL.add(WBLRanked.getData()[i] as WordData)
                    if (i == 512) {
                        break
                    }
                }

                val SD = SiteData()
                var description = content!!
                if (description.length >= 128) {
                    description = description.substring(0, 128) + "..."
                }
                SD.setDescription(description)
                SD.setTitle(title)
                SD.setAddress(address)
                SD.setTerms(WBL)
                SDBL.add(SD)

                for (i in 0 until WBL.getData().size) {
                    val WD = WBL.getData()[i] as WordData
                    val SI = SiteIndex()
                    SI.setAddress(address)
                    SI.setRank(WD.getFrequency())
                    val WI = MyInverseLookup.get(WD.getData()) as WordIndex?
                    var RBL: RankBinaryList? = null
                    var SBL: SiteBinaryList? = null

                    if (WI != null) {
                        RBL = WI.getRankBinaryList()

                        while (RBL.get(SI.getRank()) != null) {
                            SI.setRank(SI.getRank() + 0.0000001)
                        }

                        SBL = WI.getSiteBinaryList()
                        WI.addSite(SI)
                    } else if (MyInverseLookup.getData().size < 100000) {
                        val NewWI = WordIndex(WD.getData(), "")
                        NewWI.addSite(SI)
                        MyInverseLookup.add(NewWI)
                    }
                }
            }
        }
    }

    fun indexPage(title: String, address: String, content: String) {
        try {
            available.acquire()
            ExecuteStack.add(indexPageTask(title, address, content))
            currentlyIndexed++
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            available.release()
        }
    }

    override fun run() {
        while (true) {
            val startTime = MyTime!!.getCurrentTime()
            try {
                available.acquire()
                val MyIterator = ExecuteStack.iterator()

                while (MyIterator.hasNext()) {
                    val MyTask = MyIterator.next() as Task
                    MyTask.execute()
                    MyIterator.remove()
                }

                available.release()

                if (currentlyIndexed < fileCount + 1) {
                    try {
                        val LX = LoadXML()
                        var Data = ""
                        var ip = ""
                        val C = sql(Connection, DB, Username, Password)
                        var result: ArrayList<Any?>? = null
                        var Q = "select stats,ip from user where num=" + (currentlyIndexed + 1) + ";"
                        result = C.process(Q)
                        if (result != null) {
                            Data = result[0] as String
                            ip = result[1] as String
                        }
                        Q = "SELECT TO_DAYS(NOW())-TO_DAYS(last_logged_in),npc FROM hackerforum.users WHERE ip='" + ip + "'"
                        result = C.process(Q)
                        if ((Integer.parseInt(result!![0] as String) < 14) || ((result[1] as String).equals('Y'))) {
                            LX.loadByteArray(Data.toByteArray())

                            var address = ""
                            var title = ""
                            var content = ""

                            var N: Node? = LX.findNodeRecursive("ip", 0)
                            N = LX.findNodeRecursive(N, "#text", 0)
                            if (N != null) {
                                address = N.nodeValue
                            }

                            N = LX.findNodeRecursive("title", 0)
                            N = LX.findNodeRecursive(N, "#text", 0)
                            if (N != null) {
                                title = N.nodeValue
                            }

                            N = LX.findNodeRecursive("body", 0)
                            N = LX.findNodeRecursive(N, "#text", 0)
                            if (N != null) {
                                content = N.nodeValue
                            }

                            content = content.replace(Regex("\\<.*?\\>"), "")
                            content = content.replace(Regex("\\&.*?;"), "")
                            content = content.replace(Regex("\\n"), "")

                            indexPage(title, address, content.lowercase())

                            if (currentlyIndexed == fileCount) {
                                fileList = null
                            }
                            if (count % 100 == 0) {
                                System.out.println("Current Index: " + count)
                            }
                            count++
                        } else {
                            if (count % 100 == 0) {
                                System.out.println("Current Index: " + count)
                            }
                            currentlyIndexed++
                            count++
                        }
                        C.close()
                    } catch (e: Exception) {
                        currentlyIndexed++
                        count++
                    }
                }
            } catch (e: Exception) {
            } finally {
                available.release()
            }

            try {
                val endTime = MyTime!!.getCurrentTime()
                if (sleepTime - (endTime - startTime) > 0) {
                    Thread.sleep(sleepTime - (endTime - startTime))
                }
            } catch (e: Exception) {
            }
        }
    }

    fun getLoaded(): Boolean {
        if (currentlyIndexed >= fileCount && ExecuteStack.size == 0) {
            return true
        }
        return false
    }

    companion object {
        const val STOP_WORD_LOCATION = "stop_words.txt"
        private const val sleepTime = 50L
        private const val ReturnCount = 10

        @JvmField
        var MySearchHandler: SearchHandler? = null

        @JvmStatic
        fun getInstance(MyServer: SearchServer?): SearchHandler {
            if (MySearchHandler == null) {
                MySearchHandler = SearchHandler(MyServer)
            }

            return MySearchHandler!!
        }
    }
}
