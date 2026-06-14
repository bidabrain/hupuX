package com.hupux.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "followed_zones")
data class FollowedZoneEntity(
    @PrimaryKey val topicId: Int,
    val topicName: String,
    val topicLogo: String,
    val followedAt: Long = System.currentTimeMillis()
)
