package com.hupux.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.hupux.data.local.FavoriteEntity
import com.hupux.shared.db.HupuDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FavoritesRepository(private val db: HupuDatabase) {

    fun getAll(): Flow<List<FavoriteEntity>> =
        db.favoritesQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toEntity() } }

    suspend fun isFavorite(tid: String): Boolean = withContext(Dispatchers.IO) {
        db.favoritesQueries.isFavorite(tid).executeAsOne()
    }

    suspend fun add(entity: FavoriteEntity) = withContext(Dispatchers.IO) {
        db.favoritesQueries.insert(
            tid      = entity.tid,
            title    = entity.title,
            url      = entity.url,
            label    = entity.label,
            replies  = entity.replies.toLong(),
            imageUrl = entity.imageUrl,
            savedAt  = entity.savedAt
        )
    }

    suspend fun remove(tid: String) = withContext(Dispatchers.IO) {
        db.favoritesQueries.deleteByTid(tid)
    }

    suspend fun toggle(entity: FavoriteEntity) {
        if (isFavorite(entity.tid)) remove(entity.tid) else add(entity)
    }

    private fun com.hupux.shared.db.Favorites.toEntity() = FavoriteEntity(
        tid      = tid,
        title    = title,
        url      = url,
        label    = label,
        replies  = replies.toInt(),
        imageUrl = imageUrl,
        savedAt  = savedAt
    )
}
