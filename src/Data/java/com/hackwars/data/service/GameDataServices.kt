package com.hackwars.data.service

import com.hackwars.data.DataModule
import com.hackwars.data.model.ForumActivity
import com.hackwars.data.model.ForumLoginSnapshot
import com.hackwars.data.model.DropItemData
import com.hackwars.data.model.NetworkDefinition
import com.hackwars.data.model.PendingPurchase
import com.hackwars.data.model.SearchBootstrapRow
import com.hackwars.data.repository.AuthRepository
import com.hackwars.data.repository.DomainRepository
import com.hackwars.data.repository.DropTableRepository
import com.hackwars.data.repository.NetworkRepository
import com.hackwars.data.repository.PlayStatisticsRepository
import com.hackwars.data.repository.SearchBootstrapRepository
import com.hackwars.data.repository.StorePurchaseRepository
import com.hackwars.data.repository.UserSaveRepository

interface GameAuthDataService {
    fun authenticate(username: String, loginPassword: String, passwordIniValue: String?): Boolean

    fun findForumLoginSnapshotByName(username: String): ForumLoginSnapshot?

    fun findForumActivityByIp(ip: String): ForumActivity?

    fun markForumLoginNow(username: String)
}

interface GameProfileDataService {
    fun findProfileXmlByIp(ip: String): String?

    fun upsertProfileXmlByIp(ip: String, xml: String)
}

interface GameTelemetryDataService {
    fun recordPlayWindowByIp(ip: String, startTime: Long, endTime: Long)
}

interface GameWorldDataService {
    fun loadNetworkDefinitions(): List<NetworkDefinition>

    fun findDropItems(dropId: Int): List<DropItemData>

    fun findDomainByIp(ip: String): String?

    fun findPendingPurchasesByIp(ip: String): List<PendingPurchase>

    fun markPurchasesGiven(ids: Collection<Long>)
}

interface GameSearchDataService {
    fun findBootstrapRows(): List<SearchBootstrapRow>
}

class DefaultGameAuthDataService(
    private val dataModule: DataModule,
) : GameAuthDataService {
    override fun authenticate(username: String, loginPassword: String, passwordIniValue: String?): Boolean {
        return dataModule.withEntityManager { entityManager ->
            val repository = AuthRepository(entityManager)
            if (loginPassword != passwordIniValue) {
                repository.drupalPasswordMatches(username, loginPassword)
            } else {
                repository.forumUserExists(username)
            }
        }
    }

    override fun findForumLoginSnapshotByName(username: String): ForumLoginSnapshot? {
        return dataModule.withEntityManager { entityManager ->
            AuthRepository(entityManager).findForumLoginSnapshotByName(username)
        }
    }

    override fun findForumActivityByIp(ip: String): ForumActivity? {
        return dataModule.withEntityManager { entityManager ->
            AuthRepository(entityManager).findForumActivityByIp(ip)
        }
    }

    override fun markForumLoginNow(username: String) {
        dataModule.withTransaction { entityManager ->
            AuthRepository(entityManager).updateForumLastLoggedInNow(username)
        }
    }
}

class DefaultGameProfileDataService(
    private val dataModule: DataModule,
) : GameProfileDataService {
    override fun findProfileXmlByIp(ip: String): String? {
        return dataModule.withEntityManager { entityManager ->
            UserSaveRepository(entityManager).findStatsXmlByIp(ip)
        }
    }

    override fun upsertProfileXmlByIp(ip: String, xml: String) {
        dataModule.withTransaction { entityManager ->
            UserSaveRepository(entityManager).upsertStatsXmlByIp(ip, xml)
        }
    }
}

class DefaultGameTelemetryDataService(
    private val dataModule: DataModule,
) : GameTelemetryDataService {
    override fun recordPlayWindowByIp(ip: String, startTime: Long, endTime: Long) {
        dataModule.withTransaction { entityManager ->
            val authRepository = AuthRepository(entityManager)
            val uid = authRepository.findDrupalUidByIp(ip) ?: return@withTransaction
            PlayStatisticsRepository(entityManager).insertSession(uid, startTime, endTime)
        }
    }
}

class DefaultGameWorldDataService(
    private val dataModule: DataModule,
) : GameWorldDataService {
    override fun loadNetworkDefinitions(): List<NetworkDefinition> {
        return dataModule.withEntityManager { entityManager ->
            val networkRepository = NetworkRepository(entityManager)
            networkRepository.findNetworks().map { summary ->
                NetworkDefinition(
                    id = summary.id,
                    name = summary.name,
                    attackProbability = summary.attackProbability,
                    attachedNetworks = networkRepository.findAttachedNetworks(summary.id),
                    storeNpcs = networkRepository.findNpcsByType(summary.id, "store"),
                    miningNpcs = networkRepository.findNpcsByType(summary.id, "mining"),
                    attackNpcs = networkRepository.findNpcsByType(summary.id, "attack"),
                    questNpcs = networkRepository.findNpcsByType(summary.id, "quest"),
                )
            }
        }
    }

    override fun findDropItems(dropId: Int): List<DropItemData> {
        return dataModule.withEntityManager { entityManager ->
            DropTableRepository(entityManager).findDropItems(dropId)
        }
    }

    override fun findDomainByIp(ip: String): String? {
        return dataModule.withEntityManager { entityManager ->
            DomainRepository(entityManager).findDomainByIp(ip)
        }
    }

    override fun findPendingPurchasesByIp(ip: String): List<PendingPurchase> {
        return dataModule.withEntityManager { entityManager ->
            StorePurchaseRepository(entityManager).findPendingPurchasesByIp(ip)
        }
    }

    override fun markPurchasesGiven(ids: Collection<Long>) {
        if (ids.isEmpty()) {
            return
        }
        dataModule.withTransaction { entityManager ->
            val repository = StorePurchaseRepository(entityManager)
            ids.forEach(repository::markAsGiven)
        }
    }
}

class DefaultGameSearchDataService(
    private val dataModule: DataModule,
) : GameSearchDataService {
    override fun findBootstrapRows(): List<SearchBootstrapRow> {
        return dataModule.withEntityManager { entityManager ->
            SearchBootstrapRepository(entityManager).findBootstrapRows()
        }
    }
}
