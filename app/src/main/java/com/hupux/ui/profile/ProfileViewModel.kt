package com.hupux.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.local.CookiePreferences
import com.hupux.data.model.UserProfile
import com.hupux.data.model.Zone
import com.hupux.data.repository.ProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel constructor(
    private val repo: ProfileRepository,
    private val cookiePrefs: CookiePreferences
) : ViewModel() {

    sealed class State {
        object NotLoggedIn : State()
        object Loading : State()
        data class Success(
            val profile: UserProfile,
            val followedZones: List<Zone> = emptyList(),
            val favoriteCountStr: String = "",
            val unreadMessageCount: Int = 0
        ) : State()
        data class Error(val msg: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.NotLoggedIn)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            cookiePrefs.changeFlow.collect { checkLogin() }
        }
        checkLogin()
    }

    fun checkLogin() {
        if (!repo.isLoggedIn()) { _state.value = State.NotLoggedIn; return }
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _state.value = State.Loading
            try {
                val profileDeferred  = async { repo.fetchProfile() }
                val zonesDeferred    = async { runCatching { repo.fetchFollowedZones() }.getOrDefault(emptyList()) }
                val favDeferred      = async { runCatching { repo.fetchFavoriteList() }.getOrNull() }
                val unreadDeferred   = async { runCatching { repo.fetchUnreadMessageCount() }.getOrDefault(0) }
                val favPage          = favDeferred.await()
                val favoriteCountStr = when {
                    favPage == null              -> ""
                    favPage.hasMore              -> "${favPage.threads.size}+"
                    else                         -> favPage.threads.size.toString()
                }
                _state.value = State.Success(
                    profile             = profileDeferred.await(),
                    followedZones       = zonesDeferred.await(),
                    favoriteCountStr    = favoriteCountStr,
                    unreadMessageCount  = unreadDeferred.await()
                )
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: "加载失败")
            }
        }
    }
}
