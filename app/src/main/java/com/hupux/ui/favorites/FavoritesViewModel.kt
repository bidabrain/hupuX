package com.hupux.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.repository.FavoritesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FavoritesViewModel constructor(private val repo: FavoritesRepository) : ViewModel() {
    val favorites = repo.getAll()

    fun remove(tid: String) {
        viewModelScope.launch(Dispatchers.IO) { repo.remove(tid) }
    }
}
