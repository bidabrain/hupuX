package com.hupux.ui.zone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.local.CookiePreferences
import com.hupux.data.model.Post
import com.hupux.data.model.ZoneDetail
import com.hupux.data.model.ZoneSort
import com.hupux.data.repository.ZoneRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val JINGHUA_MIN_RECOMMEND = 20   // 精华 = 推荐数 ≥ 20

const val TAB_REPLY   = 0   // 最新回复（移动版默认顺序）
const val TAB_POST    = 1   // 最新发布（桌面版 postdate）
const val TAB_JINGHUA = 2   // 精华：在「最新回复」列表上按推荐数筛

data class ZoneDetailUiState(
    val zoneDetail:    ZoneDetail? = null,
    val replyPosts:    List<Post>  = emptyList(),   // 最新回复，精华 tab 也从这里筛
    val replyCursor:   String?     = null,
    val postPosts:     List<Post>  = emptyList(),   // 最新发布
    val postCursor:    String?     = null,
    val postLoaded:    Boolean     = false,
    val selectedTab:   Int         = TAB_REPLY,
    val isLoading:     Boolean     = false,          // 首次进专区
    val isPostLoading: Boolean     = false,          // 首次切到「最新发布」
    val isLoadingMore: Boolean     = false,
    val error:         String?     = null,
    val postError:     String?     = null,
    val isLoggedIn:    Boolean     = false
) {
    val posts: List<Post> get() = when (selectedTab) {
        TAB_POST    -> postPosts
        TAB_JINGHUA -> replyPosts.filter { it.recommendNum >= JINGHUA_MIN_RECOMMEND }
        else        -> replyPosts
    }
    val nextCursor: String? get() = if (selectedTab == TAB_POST) postCursor else replyCursor
    val showLoading: Boolean get() = if (selectedTab == TAB_POST) isPostLoading else isLoading
    val shownError: String? get() = if (selectedTab == TAB_POST) postError else error
}

class ZoneDetailViewModel constructor(
    private val repo: ZoneRepository,
    private val cookiePrefs: CookiePreferences
) : ViewModel() {

    private val _state = MutableStateFlow(ZoneDetailUiState(isLoading = true, isLoggedIn = cookiePrefs.isLoggedIn))
    val state = _state.asStateFlow()

    private var topicId: Int = 0

    fun init(id: Int) {
        if (topicId == id && _state.value.replyPosts.isNotEmpty()) return
        topicId = id
        load()
    }

    /** 重新加载当前 tab；「最新回复」顺带刷新专区信息 */
    fun load() {
        if (_state.value.selectedTab == TAB_POST) { loadPostFirst(); return }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, replyPosts = emptyList(), replyCursor = null, error = null) }
            runCatching { repo.getZonePosts(topicId) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            zoneDetail  = page.zoneDetail,
                            replyPosts  = page.posts,
                            replyCursor = page.nextCursor,
                            isLoading   = false
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
                }
        }
    }

    private fun loadPostFirst() {
        viewModelScope.launch {
            _state.update { it.copy(isPostLoading = true, postError = null) }
            runCatching { repo.getZonePosts(topicId, null, ZoneSort.POST) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(postPosts = page.posts, postCursor = page.nextCursor,
                                postLoaded = true, isPostLoading = false)
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isPostLoading = false, postError = e.message ?: "加载失败") }
                }
        }
    }

    fun selectTab(index: Int) {
        val s = _state.value
        if (s.selectedTab == index) return
        _state.update { it.copy(selectedTab = index) }
        when {
            // 「最新发布」首次切过来才联网，之后来回切不重复加载
            index == TAB_POST && !s.postLoaded && !s.isPostLoading -> loadPostFirst()
            // 精华是在已加载的帖子里筛，筛完为空就自动往后翻
            index == TAB_JINGHUA && _state.value.posts.isEmpty() && s.replyCursor != null -> loadMore()
        }
    }

    fun loadMore() {
        val s = _state.value
        val sort = if (s.selectedTab == TAB_POST) ZoneSort.POST else ZoneSort.REPLY
        val cursor = s.nextCursor ?: return
        if (s.isLoadingMore) return
        // 同步置位，防止协程启动前多次调用都通过检查
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            runCatching { repo.getZonePosts(topicId, cursor, sort) }
                .onSuccess { page ->
                    _state.update {
                        if (sort == ZoneSort.POST) {
                            val existing = it.postPosts.map { p -> p.tid }.toHashSet()
                            it.copy(postPosts = it.postPosts + page.posts.filter { p -> p.tid !in existing },
                                    postCursor = page.nextCursor, isLoadingMore = false)
                        } else {
                            val existing = it.replyPosts.map { p -> p.tid }.toHashSet()
                            it.copy(replyPosts = it.replyPosts + page.posts.filter { p -> p.tid !in existing },
                                    replyCursor = page.nextCursor, isLoadingMore = false)
                        }
                    }
                }
                .onFailure {
                    _state.update { it.copy(isLoadingMore = false) }
                }
        }
    }
}
