package com.hupux.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hupux.data.local.FollowedZoneEntity
import com.hupux.data.model.Zone
import com.hupux.data.ioDispatcher
import com.hupux.data.nowMillis
import com.hupux.shared.db.HupuDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FollowedZonesRepository(private val db: HupuDatabase) {

    fun getAll(): Flow<List<FollowedZoneEntity>> =
        db.followedZonesQueries.selectAll()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { list -> list.map { it.toEntity() } }

    fun getAllIds(): Flow<Set<Int>> =
        db.followedZonesQueries.selectAllIds()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { list -> list.map { it.toInt() }.toSet() }

    /** 一次性快照（供 iOS/Swift 端读取，避免消费 Kotlin Flow）。 */
    suspend fun getAllOnce(): List<FollowedZoneEntity> = withContext(ioDispatcher) {
        db.followedZonesQueries.selectAll().executeAsList().map { it.toEntity() }
    }

    suspend fun isFollowed(topicId: Int): Boolean = withContext(ioDispatcher) {
        db.followedZonesQueries.isFollowed(topicId.toLong()).executeAsOne()
    }

    suspend fun toggle(zone: Zone) {
        if (isFollowed(zone.topicId)) {
            withContext(ioDispatcher) {
                db.followedZonesQueries.deleteById(zone.topicId.toLong())
            }
        } else {
            withContext(ioDispatcher) {
                db.followedZonesQueries.insert(
                    topicId    = zone.topicId.toLong(),
                    topicName  = zone.topicName,
                    topicLogo  = zone.topicLogo,
                    followedAt = nowMillis()
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
