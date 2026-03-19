package game.computer.session

fun interface LocalSaveOverrideSource {
    fun load(ip: String, active: Boolean): String?
}

object ComputerSessionOverrides {
    @Volatile
    private var localSaveOverrideSource: LocalSaveOverrideSource? = null

    @JvmStatic
    fun localSaveXml(ip: String, active: Boolean): String? {
        return localSaveOverrideSource?.load(ip, active)
    }

    @JvmStatic
    fun installLocalSaveOverride(source: LocalSaveOverrideSource?) {
        localSaveOverrideSource = source
    }

    @JvmStatic
    fun reset() {
        localSaveOverrideSource = null
    }
}

