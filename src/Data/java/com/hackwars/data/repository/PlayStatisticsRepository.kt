package com.hackwars.data.repository

import com.hackwars.data.entity.hackwars.UserPlayStatisticEntity
import com.hackwars.data.entity.hackwars.UserPlayStatisticId
import jakarta.persistence.EntityManager

class PlayStatisticsRepository(private val entityManager: EntityManager) {
    /**
     * Legacy mapping:
     * INSERT INTO hackwars.user_play_statistics VALUES (uid,starttime,endtime)
     */
    fun insertSession(uid: Long, startTime: Long, endTime: Long) {
        val entity = UserPlayStatisticEntity()
        entity.id = UserPlayStatisticId(uid, startTime, endTime)
        entityManager.persist(entity)
    }
}
