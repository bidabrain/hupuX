package com.hupux.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.model.UserThread
import com.hupux.data.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserFavoriteListViewModel(
    private val profileRepo: ProfileRepository
) : ViewModel() {

    data class State(
        val items: List<UserThread> = emptyList(),
        val isLoading: Boolean = false,
        val hasMore: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var nextMaxTime = 0L

    init { loadInternal() }

    fun load() {
        if (_state.value.isLoading) return
        loadInternal()
    }

    private fun loadInternal() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val page = profileRepo.fetchFavoriteList(nextMaxTime)
                nextMaxTime = page.nextMaxTime
                _state.value = _state.value.copy(
                    items     = _state.value.items + page.threads,
                    hasMore   = page.hasMore,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun refresh() {
        nextMaxTime = 0L
        _state.value = State()
        loadInternal()
    }
}
