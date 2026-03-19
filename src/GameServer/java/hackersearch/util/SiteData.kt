package hackersearch.util

import com.plink.dolphinstem.WordBinaryList
import java.io.Serializable

class SiteData : Serializable {
    private var address: String? = null
    private var description: String? = null
    private var title: String? = null
    private var myTerms: WordBinaryList? = null

    fun setAddress(address: String?) {
        this.address = address
    }

    fun getAddress(): String {
        return unsafeValue(address)
    }

    fun setDescription(description: String?) {
        this.description = description
    }

    fun getDescription(): String {
        return unsafeValue(description)
    }

    fun setTitle(title: String?) {
        this.title = title
    }

    fun getTitle(): String {
        return unsafeValue(title)
    }

    fun setTerms(MyTerms: WordBinaryList?) {
        this.myTerms = MyTerms
    }

    fun getTerms(): WordBinaryList {
        return unsafeValue(myTerms)
    }

    private fun <T> unsafeValue(value: Any?): T {
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}
