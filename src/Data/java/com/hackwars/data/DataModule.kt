package com.hackwars.data

import com.hackwars.data.config.DataEntityManagerFactory
import com.hackwars.data.config.DataJpaProperties
import com.hackwars.data.service.DefaultGameAuthDataService
import com.hackwars.data.service.DefaultGameProfileDataService
import com.hackwars.data.service.DefaultGameSearchDataService
import com.hackwars.data.service.DefaultGameTelemetryDataService
import com.hackwars.data.service.DefaultGameWorldDataService
import com.hackwars.data.service.GameAuthDataService
import com.hackwars.data.service.GameProfileDataService
import com.hackwars.data.service.GameSearchDataService
import com.hackwars.data.service.GameTelemetryDataService
import com.hackwars.data.service.GameWorldDataService
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory

class DataModule(properties: DataJpaProperties) : AutoCloseable {
    private val managedEntityManagerFactory = DataEntityManagerFactory.create(properties)
    private val emf: EntityManagerFactory = managedEntityManagerFactory.entityManagerFactory

    val gameAuthDataService: GameAuthDataService = DefaultGameAuthDataService(this)
    val gameProfileDataService: GameProfileDataService = DefaultGameProfileDataService(this)
    val gameTelemetryDataService: GameTelemetryDataService = DefaultGameTelemetryDataService(this)
    val gameWorldDataService: GameWorldDataService = DefaultGameWorldDataService(this)
    val gameSearchDataService: GameSearchDataService = DefaultGameSearchDataService(this)

    fun warmup() {
        withEntityManager { entityManager ->
            entityManager.createNativeQuery("SELECT 1").singleResult
        }
    }

    fun <T> withEntityManager(work: (EntityManager) -> T): T {
        val entityManager = emf.createEntityManager()
        return try {
            work(entityManager)
        } finally {
            entityManager.close()
        }
    }

    fun <T> withTransaction(work: (EntityManager) -> T): T {
        val entityManager = emf.createEntityManager()
        val tx = entityManager.transaction
        try {
            tx.begin()
            val result = work(entityManager)
            tx.commit()
            return result
        } catch (ex: RuntimeException) {
            if (tx.isActive) {
                tx.rollback()
            }
            throw ex
        } finally {
            entityManager.close()
        }
    }

    override fun close() {
        managedEntityManagerFactory.close()
    }
}
