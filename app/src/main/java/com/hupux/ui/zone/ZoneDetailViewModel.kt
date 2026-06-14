package com.hupux.ui.zone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.model.Post
import com.hupux.data.model.ZoneDetail
import com.hupux.data.repository.ZoneRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val JINGHUA_MIN_RECOMMEND = 20   // 精华 = 推荐数 ≥ 20

data class ZoneDetailUiState(
    val zoneDetail:    ZoneDetail? = null,
    val allPosts:      List<Post>  = emptyList(),   // 全量，供两个 tab 共用
    val selectedTab:   Int         = 0,              // 0=全部  1=精华
    val isLoading:     Boolean     = false,
    val isLoadingMore: Boolean     = false,
    val error:         String?     = null,
    val nextCursor:    String?     = null
) {
    // 精华 = recommendNum ≥ 阈值
    val posts: List<Post> get() = if (selectedTab == 0) allPosts
                                  else allPosts.filter { it.recommendNum >= JINGHUA_MIN_RECOMMEND }
}

@HiltViewModel
class ZoneDetailViewModel @Inject constructor(private val repo: ZoneRepository) : ViewModel() {

    private val _state = MutableStateFlow(ZoneDetailUiState(isLoading = true))
    val state = _state.asStateFlow()

    private var topicId: Int = 0

    fun init(id: Int) {
        if (topicId == id && _state.value.allPosts.isNotEmpty()) return
        topicId = id
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, allPosts = emptyList(), nextCursor = null, error = null)
            runCatching { repo.getZonePosts(topicId) }
                .onSuccess { page ->
                    _state.value = _state.value.copy(
                        zoneDetail = page.zoneDetail,
                        allPosts   = page.posts,
                        nextCursor = page.nextCursor,
                        isLoading  = false
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "加载失败")
                }
        }
    }

    fun selectTab(index: Int) {
        if (_state.value.selectedTab == index) return
        _state.value = _state.value.copy(selectedTab = index)
        // 如果切到精华且当前精华列表为空，自动加载更多
        if (index == 1 && _state.value.posts.isEmpty() && _state.value.nextCursor != null) {
            loadMore()
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.isLoadingMore) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingMore = true)
            runCatching { repo.getZonePosts(topicId, cursor) }
                .onSuccess { page ->
                    _state.value = _state.value.copy(
                        allPosts      = _state.value.allPosts + page.posts,
                        nextCursor    = page.nextCursor,
                        isLoadingMore = false
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(isLoadingMore = false)
                }
        }
    }
}
