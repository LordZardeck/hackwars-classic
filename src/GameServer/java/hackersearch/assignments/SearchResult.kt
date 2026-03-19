package hackersearch.assignments

import java.io.Serializable

class SearchResult : Serializable {
    private var title: String? = null
    private var description: String? = null
    private var address: String? = null

    fun setTitle(title: String?) {
        this.title = title
    }

    fun getTitle(): String {
        return unsafeValue(title)
    }

    fun setDescription(description: String?) {
        this.description = description
    }

    fun getDescription(): String {
        return unsafeValue(description)
    }

    fun setAddress(address: String?) {
        this.address = address
    }

    fun getAddress(): String {
        return unsafeValue(address)
    }

    private fun <T> unsafeValue(value: Any?): T {
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}
