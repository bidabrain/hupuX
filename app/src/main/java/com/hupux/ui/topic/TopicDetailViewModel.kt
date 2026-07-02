package com.hupux.ui.topic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.model.Post
import com.hupux.data.model.TopicInfo
import com.hupux.data.repository.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TopicDetailUiState(
    val info:          TopicInfo?  = null,
    val posts:         List<Post>  = emptyList(),
    val isLoading:     Boolean     = false,
    val isLoadingMore: Boolean     = false,
    val error:         String?     = null,
    val nextPage:      Int?        = null
)

class TopicDetailViewModel(
    private val repo: HomeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TopicDetailUiState(isLoading = true))
    val state = _state.asStateFlow()

    private var tagId: Long = 0

    fun init(id: Long) {
        if (tagId == id && _state.value.posts.isNotEmpty()) return
        tagId = id
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true, posts = emptyList(), nextPage = null, error = null)
            runCatching { repo.getTopicThreads(tagId, 1) }
                .onSuccess { page ->
                    _state.value = _state.value.copy(
                        info      = page.info,
                        posts     = page.posts,
                        nextPage  = page.nextPage,
                        isLoading = false
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "加载失败")
                }
        }
    }

    fun loadMore() {
        val next = _state.value.nextPage ?: return
        if (_state.value.isLoadingMore) return
        _state.value = _state.value.copy(isLoadingMore = true)
        viewModelScope.launch {
            runCatching { repo.getTopicThreads(tagId, next) }
                .onSuccess { page ->
                    val existing = _state.value.posts.map { it.tid }.toHashSet()
                    val newPosts = page.posts.filter { it.tid !in existing }
                    _state.value = _state.value.copy(
                        posts         = _state.value.posts + newPosts,
                        nextPage      = page.nextPage,
                        isLoadingMore = false
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(isLoadingMore = false)
                }
        }
    }
}
