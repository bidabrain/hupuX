package com.hupux.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowedZoneDao {
    @Query("SELECT * FROM followed_zones ORDER BY followedAt DESC")
    fun getAll(): Flow<List<FollowedZoneEntity>>

    @Query("SELECT topicId FROM followed_zones")
    fun getAllIds(): Flow<List<Int>>

    @Query("SELECT EXISTS(SELECT 1 FROM followed_zones WHERE topicId = :topicId)")
    suspend fun isFollowed(topicId: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FollowedZoneEntity)

    @Query("DELETE FROM followed_zones WHERE topicId = :topicId")
    suspend fun delete(topicId: Int)
}
