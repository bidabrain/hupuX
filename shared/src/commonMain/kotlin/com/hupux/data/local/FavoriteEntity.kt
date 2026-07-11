package com.hupux.data.local

import com.hupux.data.nowMillis

data class FavoriteEntity(
    val tid: String,
    val title: String,
    val url: String,
    val label: String,
    val replies: Int,
    val imageUrl: String,
    val savedAt: Long = nowMillis()
)
