package game.computer.session

import com.hackwars.data.model.PersistedProfileSave

fun interface LocalSaveOverrideSource {
    fun load(ip: String, active: Boolean): PersistedProfileSave?
}

fun interface LocalSaveXmlOverrideSource {
    fun load(ip: String, active: Boolean): String?
}

object ComputerSessionOverrides {
    @Volatile
    private var localSaveOverrideSource: LocalSaveOverrideSource? = null

    @JvmStatic
    fun localSave(ip: String, active: Boolean): PersistedProfileSave? {
        return localSaveOverrideSource?.load(ip, active)
    }

    @JvmStatic
    fun localSaveXml(ip: String, active: Boolean): String? {
        return localSave(ip, active)?.legacyXml
    }

    @JvmStatic
    fun installLocalSaveOverride(source: LocalSaveOverrideSource?) {
        localSaveOverrideSource = source
    }

    @JvmStatic
    fun installLocalSaveXmlOverride(source: LocalSaveXmlOverrideSource?) {
        localSaveOverrideSource = if (source == null) {
            null
        } else {
            LocalSaveOverrideSource { ip, active ->
                val xml = source.load(ip, active) ?: return@LocalSaveOverrideSource null
                PersistedProfileSave(
                    userNum = -1,
                    ip = ip,
                    legacyXml = xml,
                    statsJson = null,
                    statsJsonVersion = null,
                    statsJsonMigratedAt = null,
                    blobs = emptyList(),
                )
            }
        }
    }

    @JvmStatic
    fun reset() {
        localSaveOverrideSource = null
    }
}
