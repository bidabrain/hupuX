package com.hupux.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.local.CookiePreferences
import com.hupux.data.model.UserProfile
import com.hupux.data.model.Zone
import com.hupux.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repo: ProfileRepository,
    private val cookiePrefs: CookiePreferences
) : ViewModel() {

    sealed class State {
        object NotLoggedIn : State()
        object Loading : State()
        data class Success(val profile: UserProfile, val followedZones: List<Zone> = emptyList()) : State()
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
                val profileDeferred = async { repo.fetchProfile() }
                val zonesDeferred   = async { runCatching { repo.fetchFollowedZones() }.getOrDefault(emptyList()) }
                _state.value = State.Success(profileDeferred.await(), zonesDeferred.await())
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: "加载失败")
            }
        }
    }
}
