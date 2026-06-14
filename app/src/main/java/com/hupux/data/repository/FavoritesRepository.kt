package com.hupux.data.repository

import com.hupux.data.local.FavoriteDao
import com.hupux.data.local.FavoriteEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(private val dao: FavoriteDao) {
    fun getAll(): Flow<List<FavoriteEntity>> = dao.getAll()

    suspend fun isFavorite(tid: String): Boolean = dao.isFavorite(tid)

    suspend fun add(entity: FavoriteEntity) = dao.insert(entity)

    suspend fun remove(tid: String) = dao.delete(tid)

    suspend fun toggle(entity: FavoriteEntity) {
        if (dao.isFavorite(entity.tid)) dao.delete(entity.tid) else dao.insert(entity)
    }
}
