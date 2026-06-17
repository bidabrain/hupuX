package com.hupux.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hupux.data.local.FollowedZoneEntity
import com.hupux.data.model.Zone
import com.hupux.shared.db.HupuDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FollowedZonesRepository(private val db: HupuDatabase) {

    fun getAll(): Flow<List<FollowedZoneEntity>> =
        db.followedZonesQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toEntity() } }

    fun getAllIds(): Flow<Set<Int>> =
        db.followedZonesQueries.selectAllIds()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toInt() }.toSet() }

    suspend fun isFollowed(topicId: Int): Boolean = withContext(Dispatchers.IO) {
        db.followedZonesQueries.isFollowed(topicId.toLong()).executeAsOne()
    }

    suspend fun toggle(zone: Zone) {
        if (isFollowed(zone.topicId)) {
            withContext(Dispatchers.IO) {
                db.followedZonesQueries.deleteById(zone.topicId.toLong())
            }
        } else {
            withContext(Dispatchers.IO) {
                db.followedZonesQueries.insert(
                    topicId    = zone.topicId.toLong(),
                    topicName  = zone.topicName,
                    topicLogo  = zone.topicLogo,
                    followedAt = System.currentTimeMillis()
                )
            }
        }
    }

    private fun com.hupux.shared.db.Followed_zones.toEntity() = FollowedZoneEntity(
        topicId    = topicId.toInt(),
        topicName  = topicName,
        topicLogo  = topicLogo,
        followedAt = followedAt
    )
}
