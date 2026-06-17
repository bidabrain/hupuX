package com.hupux.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.model.UserRecommendPost
import com.hupux.data.scraper.HupuDesktopScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserRecommendListViewModel constructor(
    private val scraper: HupuDesktopScraper,
    savedState: SavedStateHandle
) : ViewModel() {

    private val uid: String = savedState.get<String>("uid") ?: ""

    data class State(
        val items: List<UserRecommendPost> = emptyList(),
        val isLoading: Boolean = false,
        val hasMore: Boolean = true,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var page = 1

    init { loadInternal() }

    fun load() {
        if (_state.value.isLoading) return
        loadInternal()
    }

    private fun loadInternal() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val items = scraper.fetchRecommendList(uid, page)
                page++
                _state.value = _state.value.copy(
                    items     = _state.value.items + items,
                    hasMore   = items.size >= 30,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun refresh() {
        page = 1
        _state.value = State()
        loadInternal()
    }
}
