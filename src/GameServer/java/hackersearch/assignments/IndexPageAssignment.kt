package hackersearch.assignments

import com.plink.dolphinnet.Assignment
import com.plink.dolphinnet.DataHandler

class IndexPageAssignment(id: Int, title: String?, address: String?, content: String?) : Assignment(id) {
    private var title: String? = title
    private var address: String? = address
    private var content: String? = content

    fun getTitle(): String {
        return unsafeValue(title)
    }

    fun getAddress(): String {
        return unsafeValue(address)
    }

    fun getContent(): String {
        return unsafeValue(content)
    }

    override fun execute(DH: DataHandler): Any? {
        DH.addData(this)
        finish()
        return null
    }

    private fun <T> unsafeValue(value: Any?): T {
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}
