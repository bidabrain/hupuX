package com.hupux.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.model.HomeMatch
import com.hupux.data.model.HotItem
import com.hupux.data.model.Post
import com.hupux.data.repository.FollowedZonesRepository
import com.hupux.data.repository.HomeRepository
import com.hupux.data.repository.ZoneRepository
import com.hupux.data.scraper.HupuMatchScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val recommendPosts:      List<Post>       = emptyList(),
    val bannerPosts:         List<Post>       = emptyList(),
    val hotItems:            List<HotItem>    = emptyList(),
    val followedPool:        List<Post>       = emptyList(),
    val followDisplayCount:  Int              = 10,
    val followZoneCursors:   Map<Int, String> = emptyMap(),
    val selectedTab:         Int              = 0,
    val isLoading:           Boolean          = true,
    val isLoadingHot:        Boolean          = false,
    val hotError:            String?          = null,
    val isLoadingFollow:     Boolean          = false,
    val isLoadingMoreFollow: Boolean          = false,
    val error:               String?          = null
) {
    val followedPosts: List<Post> get() = followedPool.take(followDisplayCount)
    val followHasMore: Boolean    get() = followDisplayCount < followedPool.size || followZoneCursors.isNotEmpty()
}

class HomeViewModel constructor(
    private val homeRepo:     HomeRepository,
    private val zoneRepo:     ZoneRepository,
    private val followedRepo: FollowedZonesRepository,
    private val matchScraper: HupuMatchScraper
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    // Expose followed zones count so the tab can show a badge
    val followedCount = followedRepo.getAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 首页顶部「今日比分」横条。独立于 HomeUiState，避免 loadRecommend 整体重置时被清空。 */
    private val _homeMatches = MutableStateFlow<List<HomeMatch>>(emptyList())
    val homeMatches = _homeMatches.asStateFlow()

    init {
        loadRecommend()
        loadHomeMatches()
    }

    /** 比分条属于锦上添花，抓取失败就静默留空，不影响首页其余内容 */
    private fun loadHomeMatches() {
        viewModelScope.launch {
            runCatching { matchScraper.fetchHomeMatches() }
                .onSuccess { _homeMatches.value = it }
        }
    }

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
        when (index) {
            1 -> if (_state.value.hotItems.isEmpty()) loadHot()
            2 -> if (_state.value.followedPool.isEmpty()) loadFollowedFeed()
        }
    }

    fun loadHot() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingHot = true, hotError = null)
            runCatching { homeRepo.getHotItems() }
                .onSuccess { items ->
                    _state.value = _state.value.copy(hotItems = items, isLoadingHot = false)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isLoadingHot = false, hotError = it.message ?: "加载失败")
                }
        }
    }

    fun loadFollowedFeed() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingFollow = true)
            val zones = followedRepo.getAll().first()
            if (zones.isEmpty()) {
                _state.value = _state.value.copy(
                    followedPool = emptyList(), followZoneCursors = emptyMap(),
                    followDisplayCount = 10, isLoadingFollow = false
                )
                return@launch
            }
            val results = zones.map { zone ->
                async(Dispatchers.IO) {
                    runCatching { zoneRepo.getZonePosts(zone.topicId) }.getOrNull()
                        ?.let { page -> Triple(zone.topicId, page.posts.map { it.copy(label = zone.topicName) }, page.nextCursor) }
                }
            }.mapNotNull { it.await() }

            val pool    = results.flatMap { it.second }.sortedBy { parseHupuTime(it.time) }
            val cursors = results.mapNotNull { (id, _, c) -> c?.let { id to it } }.toMap()
            _state.value = _state.value.copy(
                followedPool = pool, followZoneCursors = cursors,
                followDisplayCount = 10, isLoadingFollow = false
            )
        }
    }

    fun loadMoreFollowed() {
        val s = _state.value
        if (s.isLoadingMoreFollow) return

        if (s.followDisplayCount < s.followedPool.size) {
            _state.value = s.copy(followDisplayCount = s.followDisplayCount + 10)
            return
        }
        if (s.followZoneCursors.isEmpty()) return

        _state.value = s.copy(isLoadingMoreFollow = true)
        viewModelScope.launch {
            val zones   = followedRepo.getAll().first().associateBy { it.topicId }
            val cursors = _state.value.followZoneCursors

            val results = cursors.entries.map { (topicId, cursor) ->
                async(Dispatchers.IO) {
                    val name = zones[topicId]?.topicName ?: ""
                    runCatching { zoneRepo.getZonePosts(topicId, cursor) }.getOrNull()
                        ?.let { page -> Triple(topicId, page.posts.map { it.copy(label = name) }, page.nextCursor) }
                }
            }.mapNotNull { it.await() }

            val cur     = _state.value
            val seen    = cur.followedPool.map { it.tid }.toHashSet()
            val newPool = (cur.followedPool + results.flatMap { it.second }.filter { it.tid !in seen })
                .sortedBy { parseHupuTime(it.time) }
            val newCursors = results.mapNotNull { (id, _, c) -> c?.let { id to it } }.toMap()
            _state.value = cur.copy(
                followedPool        = newPool,
                followDisplayCount  = cur.followDisplayCount + 10,
                followZoneCursors   = newCursors,
                isLoadingMoreFollow = false
            )
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
