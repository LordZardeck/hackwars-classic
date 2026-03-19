package game

import java.util.ArrayList
import java.util.concurrent.Semaphore

open class MysqlHandler {
    init {
        for (i in 0..<10) {
            CheckOutHandler()
        }
    }

    companion object {
        private val work = ArrayList<Array<Any?>>()
        private val available = Semaphore(1, true)

        @Suppress("unused")
        private val MyInstance = MysqlHandler()

        @JvmStatic
        fun getWork(): Array<Any?>? {
            try {
                available.acquire()

                if (work.size == 0) {
                    available.release()
                    return null
                }
                val o = work.removeAt(0)
                available.release()
                return o
            } catch (e: Exception) {
                available.release()
            }
            return null
        }

        @JvmStatic
        fun addWork(work1: Array<Any?>) {
            try {
                available.acquire()
                var found = false
                for (i in 0..<work.size) {
                    val ip1 = work1[0] as String
                    val ip2 = work[i][0] as String
                    if (ip2 == ip1) {
                        work[i] = work1
                        found = true
                    }
                }

                if (!found) {
                    CheckOutHandler.SAVE_COUNTER++
                    work.add(work1)
                }
                available.release()
            } catch (e: Exception) {
                available.release()
            }
        }
    }
}
