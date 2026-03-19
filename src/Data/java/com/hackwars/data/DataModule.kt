package com.hackwars.data

import com.hackwars.data.config.DataEntityManagerFactory
import com.hackwars.data.config.DataJpaProperties
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory

class DataModule(properties: DataJpaProperties) : AutoCloseable {
    private val emf: EntityManagerFactory = DataEntityManagerFactory.create(properties)

    fun <T> withEntityManager(work: (EntityManager) -> T): T {
        val entityManager = emf.createEntityManager()
        return try {
            work(entityManager)
        } finally {
            entityManager.close()
        }
    }

    fun withTransaction(work: (EntityManager) -> Unit) {
        val entityManager = emf.createEntityManager()
        val tx = entityManager.transaction
        try {
            tx.begin()
            work(entityManager)
            tx.commit()
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
        emf.close()
    }
}
