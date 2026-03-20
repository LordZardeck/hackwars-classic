package server.runtime

interface GameServerService {
    fun start()

    fun shutdown()

    suspend fun join()
}
