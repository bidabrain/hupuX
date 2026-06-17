package com.hupux.ui.zone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.model.ZoneCategory
import com.hupux.data.repository.FollowedZonesRepository
import com.hupux.data.repository.ZoneRepository
import com.hupux.data.model.Zone
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class ZoneListUiState {
    object Loading : ZoneListUiState()
    data class Success(val categories: List<ZoneCategory>) : ZoneListUiState()
    data class Error(val message: String) : ZoneListUiState()
}

class ZoneListViewModel constructor(
    private val repo: ZoneRepository,
    private val followedRepo: FollowedZonesRepository
) : ViewModel() {

    private val _state = MutableStateFlow<ZoneListUiState>(ZoneListUiState.Loading)
    val state = _state.asStateFlow()

    // Set of followed topicIds — live from DB
    val followedIds: StateFlow<Set<Int>> = followedRepo.getAllIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Followed zones list for the 我的关注 section
    val followedZones = followedRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = ZoneListUiState.Loading
            runCatching { repo.getZoneList() }
                .onSuccess { _state.value = ZoneListUiState.Success(it) }
                .onFailure { _state.value = ZoneListUiState.Error(it.message ?: "加载失败") }
        }
    }

    fun toggleFollow(zone: Zone) {
        viewModelScope.launch { followedRepo.toggle(zone) }
    }
}
