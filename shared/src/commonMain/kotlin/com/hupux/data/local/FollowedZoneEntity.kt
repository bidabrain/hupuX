package com.hupux.data.local

import com.hupux.data.nowMillis

data class FollowedZoneEntity(
    val topicId: Int,
    val topicName: String,
    val topicLogo: String,
    val followedAt: Long = nowMillis()
)
