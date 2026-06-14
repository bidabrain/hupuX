package com.hupux.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.model.Post
import com.hupux.data.repository.FollowedZonesRepository
import com.hupux.data.repository.HomeRepository
import com.hupux.data.repository.ZoneRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val recommendPosts:   List<Post> = emptyList(),
    val bannerPosts:      List<Post> = emptyList(),
    val followedPosts:    List<Post> = emptyList(),
    val selectedTab:      Int        = 0,
    val isLoading:        Boolean    = true,
    val isLoadingFollow:  Boolean    = false,
    val error:            String?    = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepo:     HomeRepository,
    private val zoneRepo:     ZoneRepository,
    private val followedRepo: FollowedZonesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    // Expose followed zones count so the tab can show a badge
    val followedCount = followedRepo.getAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init { loadRecommend() }

    fun loadRecommend() {
        viewModelScope.launch {
            _state.value = HomeUiState(isLoading = true)
            runCatching { homeRepo.getPosts() }
                .onSuccess { posts ->
                    val banner = posts.filter { it.images.isNotEmpty() }.shuffled().take(5)
                    _state.value = HomeUiState(
                        recommendPosts = posts,
                        bannerPosts    = banner,
                        isLoading      = false
                    )
                }
                .onFailure {
                    _state.value = HomeUiState(isLoading = false, error = it.message ?: "加载失败")
                }
        }
    }

    fun selectTab(index: Int) {
        _state.value = _state.value.copy(selectedTab = index)
        if (index == 1) loadFollowedFeed()
    }

    fun loadFollowedFeed() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingFollow = true)
            val zones = followedRepo.getAll().first()
            if (zones.isEmpty()) {
                _state.value = _state.value.copy(followedPosts = emptyList(), isLoadingFollow = false)
                return@launch
            }
            // Fetch all followed zones concurrently
            val posts = zones.map { zone ->
                async(Dispatchers.IO) {
                    runCatching {
                        zoneRepo.getZonePosts(zone.topicId).posts
                            .map { it.copy(label = zone.topicName) }
                    }.getOrDefault(emptyList())
                }
            }.map { it.await() }.flatten()

            val sorted = posts.sortedBy { parseHupuTime(it.time) }
            _state.value = _state.value.copy(followedPosts = sorted, isLoadingFollow = false)
        }
    }
}

// 解析虎扑相对时间为秒数（越小越新）
private fun parseHupuTime(time: String): Long = when {
    time == "刚刚"         -> 0L
    time.endsWith("分钟前") -> (time.removeSuffix("分钟前").trim().toLongOrNull() ?: 999L) * 60
    time.endsWith("小时前") -> (time.removeSuffix("小时前").trim().toLongOrNull() ?: 999L) * 3600
    time.endsWith("天前")  -> (time.removeSuffix("天前").trim().toLongOrNull() ?: 999L) * 86400
    else                  -> Long.MAX_VALUE  // 具体日期（很早的帖子）排最后
}
