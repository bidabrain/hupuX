package com.hupux.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val tid: String,
    val title: String,
    val url: String,
    val label: String,
    val replies: Int,
    val imageUrl: String,
    val savedAt: Long = System.currentTimeMillis()
)
