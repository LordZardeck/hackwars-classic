package game.data

import com.hackwars.data.DataModule
import com.hackwars.data.config.DataJpaProperties
import com.hackwars.data.service.GameAuthDataService
import com.hackwars.data.service.GameProfileDataService
import com.hackwars.data.service.GameSearchDataService
import com.hackwars.data.service.GameTelemetryDataService
import com.hackwars.data.service.GameWorldDataService

data class GameServerDataServices(
    val authService: GameAuthDataService,
    val profileService: GameProfileDataService,
    val telemetryService: GameTelemetryDataService,
    val worldService: GameWorldDataService,
    val searchService: GameSearchDataService,
)

object GameServerDataLocator : AutoCloseable {
    @Volatile
    private var overrideServices: GameServerDataServices? = null

    @Volatile
    private var managedServices: ManagedGameServerDataServices? = null

    @JvmStatic
    fun authService(): GameAuthDataService = services().authService

    @JvmStatic
    fun profileService(): GameProfileDataService = services().profileService

    @JvmStatic
    fun telemetryService(): GameTelemetryDataService = services().telemetryService

    @JvmStatic
    fun worldService(): GameWorldDataService = services().worldService

    @JvmStatic
    fun searchService(): GameSearchDataService = services().searchService

    @JvmStatic
    @Synchronized
    fun bootstrap(): GameServerDataServices {
        overrideServices?.let { return it }
        val services = services()
        managedServices?.dataModule?.warmup()
        return services
    }

    @Synchronized
    fun services(): GameServerDataServices {
        overrideServices?.let { return it }
        managedServices?.let { return it.bundle }

        val dataModule = DataModule(DataJpaProperties.localhostDefaults())
        val bundle = GameServerDataServices(
            authService = dataModule.gameAuthDataService,
            profileService = dataModule.gameProfileDataService,
            telemetryService = dataModule.gameTelemetryDataService,
            worldService = dataModule.gameWorldDataService,
            searchService = dataModule.gameSearchDataService,
        )
        managedServices = ManagedGameServerDataServices(dataModule, bundle)
        return bundle
    }

    @JvmStatic
    @Synchronized
    fun installForTests(services: GameServerDataServices) {
        managedServices?.close()
        managedServices = null
        overrideServices = services
    }

    @JvmStatic
    @Synchronized
    fun resetForTests() {
        overrideServices = null
        managedServices?.close()
        managedServices = null
    }

    @Synchronized
    override fun close() {
        resetForTests()
    }
}

private data class ManagedGameServerDataServices(
    val dataModule: DataModule,
    val bundle: GameServerDataServices,
) : AutoCloseable {
    override fun close() {
        dataModule.close()
    }
}
