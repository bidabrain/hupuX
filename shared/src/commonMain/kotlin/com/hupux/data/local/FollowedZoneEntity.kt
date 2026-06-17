package com.hupux.data.local

data class FollowedZoneEntity(
    val topicId: Int,
    val topicName: String,
    val topicLogo: String,
    val followedAt: Long = System.currentTimeMillis()
)
