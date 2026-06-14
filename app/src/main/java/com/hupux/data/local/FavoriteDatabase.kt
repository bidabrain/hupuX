package com.hupux.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities  = [FavoriteEntity::class, FollowedZoneEntity::class],
    version   = 2,
    exportSchema = false
)
abstract class FavoriteDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun followedZoneDao(): FollowedZoneDao
}
