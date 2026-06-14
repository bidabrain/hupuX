package com.hupux.data.repository

import com.hupux.data.local.FollowedZoneDao
import com.hupux.data.local.FollowedZoneEntity
import com.hupux.data.model.Zone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FollowedZonesRepository @Inject constructor(private val dao: FollowedZoneDao) {

    fun getAll(): Flow<List<FollowedZoneEntity>> = dao.getAll()

    fun getAllIds(): Flow<Set<Int>> = dao.getAllIds().map { it.toSet() }

    suspend fun isFollowed(topicId: Int): Boolean = dao.isFollowed(topicId)

    suspend fun toggle(zone: Zone) {
        if (dao.isFollowed(zone.topicId)) {
            dao.delete(zone.topicId)
        } else {
            dao.insert(
                FollowedZoneEntity(
                    topicId   = zone.topicId,
                    topicName = zone.topicName,
                    topicLogo = zone.topicLogo
                )
            )
        }
    }
}
