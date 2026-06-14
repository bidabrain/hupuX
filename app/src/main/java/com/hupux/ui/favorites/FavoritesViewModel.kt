package com.hupux.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(private val repo: FavoritesRepository) : ViewModel() {
    val favorites = repo.getAll()

    fun remove(tid: String) {
        viewModelScope.launch(Dispatchers.IO) { repo.remove(tid) }
    }
}
