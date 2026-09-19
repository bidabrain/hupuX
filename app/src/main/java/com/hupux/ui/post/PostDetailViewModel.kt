package com.hupux.ui.post

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.local.CookiePreferences
import com.hupux.data.model.Comment
import com.hupux.data.model.PostDetail
import com.hupux.data.repository.PostRepository
import com.hupux.ui.profile.ImageItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 评论排序方式 */
enum class CommentSort { DEFAULT, REVERSED, BY_LIKES }

sealed class PostDetailUiState {
    object Loading : PostDetailUiState()
    data class Success(
        val post: PostDetail,
        val subRepliesMap: Map<String, List<Comment>> = emptyMap(),
        val replyStack: List<String> = emptyList(),
        val isLoadingSubReplies: Boolean = false,
        val commentPage: Int = 1,
        val isLoadingMoreComments: Boolean = false,
        val replyingTo: Comment? = null,
        val showReplySheet: Boolean = false,
        val replyContent: String = "",
        val replyImages: List<ImageItem> = emptyList(),
        val isSubmittingReply: Boolean = false,
        val replyError: String? = null,
        val replySuccess: Boolean = false,
        val likedPids: Set<String> = emptySet(),    // 本次会话内点亮的 pid 集合
        val likingPids: Set<String> = emptySet(),   // 正在请求中的 pid，防止重复点击
        val isRecommended: Boolean = false,
        val isRecommending: Boolean = false,
        val isCollected: Boolean = false,
        val isCollecting: Boolean = false,
        val sortMode: CommentSort = CommentSort.DEFAULT,
        val sortedComments: List<Comment> = emptyList(),
        val sortedDisplayCount: Int = 0,
        val isLoadingSort: Boolean = false,
        val fullComments: List<Comment> = emptyList()   // 登录后全量拉取的完整评论（时间正序），用于本地重排
    ) : PostDetailUiState() {
        val expandedPid: String? get() = replyStack.lastOrNull()
        val displayedComments: List<Comment> get() =
            (if (sortMode == CommentSort.DEFAULT) post.comments
             else sortedComments.take(sortedDisplayCount))
                .distinctBy { it.pid }   // 保证 LazyColumn key 唯一，避免重复 pid 崩溃
        val hasMoreDisplayed: Boolean get() =
            if (sortMode == CommentSort.DEFAULT) post.hasMoreComments
            else sortedDisplayCount < sortedComments.size
    }
    data class Error(val message: String) : PostDetailUiState()
}

class PostDetailViewModel constructor(
    private val postRepo: PostRepository,
    private val cookiePrefs: CookiePreferences
) : ViewModel() {

    private val _state = MutableStateFlow<PostDetailUiState>(PostDetailUiState.Loading)
    val state = _state.asStateFlow()
    private var currentTid = ""
    private val replyUploadJobs = mutableMapOf<Uri, Job>()

    fun load(tid: String) {
        if (currentTid == tid && _state.value is PostDetailUiState.Success) return
        currentTid = tid
        viewModelScope.launch {
            _state.value = PostDetailUiState.Loading
            runCatching { postRepo.getPost(tid) }
                .onSuccess { post ->
                    // 从初始评论列表建立父子关系索引
                    val initialMap = buildSubRepliesMap(post.comments)
                    _state.value = PostDetailUiState.Success(
                        post          = post,
                        subRepliesMap = initialMap,
                        isRecommended = post.isRecommended
                    )
                }
                .onFailure { _state.value = PostDetailUiState.Error(it.message ?: "加载失败") }
        }
    }

    /** 压栈：展开某条评论的子回复，不论当前在第几层都适用 */
    fun showReplies(parentPid: String) {
        val s = _state.value as? PostDetailUiState.Success ?: return
        _state.value = s.copy(
            replyStack          = s.replyStack + parentPid,
            isLoadingSubReplies = true
        )
        viewModelScope.launch {
            runCatching { postRepo.getSubReplies(currentTid, parentPid) }
                .onSuccess { subReplies ->
                    val s2 = _state.value as? PostDetailUiState.Success ?: return@onSuccess
                    val existing = s2.subRepliesMap[parentPid] ?: emptyList()
                    val merged   = (existing + subReplies)
                        .distinctBy { it.pid }
                        .sortedBy { it.pid.toLongOrNull() ?: 0L }
                    val newMap   = s2.subRepliesMap.toMutableMap()
                        .also { it[parentPid] = merged }
                    _state.value = s2.copy(subRepliesMap = newMap, isLoadingSubReplies = false)
                }
                .onFailure {
                    val s2 = _state.value as? PostDetailUiState.Success ?: return@onFailure
                    _state.value = s2.copy(isLoadingSubReplies = false)
                }
        }
    }

    fun loadMoreComments() {
        val s = _state.value as? PostDetailUiState.Success ?: return
        if (s.isLoadingMoreComments || !s.post.hasMoreComments) return
        _state.value = s.copy(isLoadingMoreComments = true)
        val nextPage = s.commentPage + 1
        viewModelScope.launch {
            runCatching { postRepo.loadMoreComments(currentTid, nextPage) }
                .onSuccess { result ->
                    val s2 = _state.value as? PostDetailUiState.Success ?: return@onSuccess
                    val seen = s2.post.comments.map { it.pid }.toHashSet()
                    val fresh = result.first.filter { it.pid !in seen }
                    _state.value = s2.copy(
                        post = s2.post.copy(comments = s2.post.comments + fresh, hasMoreComments = result.second),
                        commentPage = nextPage,
                        isLoadingMoreComments = false
                    )
                }
                .onFailure {
                    (_state.value as? PostDetailUiState.Success)?.let {
                        _state.value = it.copy(isLoadingMoreComments = false)
                    }
                }
        }
    }

    fun setSort(mode: CommentSort) {
        val s = _state.value as? PostDetailUiState.Success ?: return
        if (s.sortMode == mode) return

        // 回到默认（时间正序）
        if (mode == CommentSort.DEFAULT) {
            _state.value = s.copy(
                sortMode = CommentSort.DEFAULT, sortedComments = emptyList(), sortedDisplayCount = 0)
            return
        }

        // 已有全量缓存（登录后拉过），或未登录用手头的 —— 直接本地重排，不重复联网
        val cached = when {
            s.fullComments.isNotEmpty() -> s.fullComments
            !cookiePrefs.isLoggedIn     -> s.post.comments
            else                        -> null
        }
        if (cached != null) {
            val sorted = applySort(cached, mode)
            _state.value = s.copy(
                sortMode = mode,
                sortedComments = sorted,
                sortedDisplayCount = minOf(REVERSE_PAGE_SIZE, sorted.size)
            )
            return
        }

        // 登录且尚未全量：把剩余页全部拉下来再排（时间正序缓存到 fullComments）
        _state.value = s.copy(isLoadingSort = true)
        viewModelScope.launch {
            val all = s.post.comments.toMutableList()
            var page = s.commentPage + 1
            while (page <= s.post.desktopTotalPages) {
                runCatching { postRepo.loadMoreComments(currentTid, page) }
                    .onSuccess { (comments, _) ->
                        val seen = all.map { it.pid }.toHashSet()
                        all.addAll(comments.filter { it.pid !in seen })
                    }
                page++
            }
            val sorted = applySort(all, mode)
            (_state.value as? PostDetailUiState.Success)?.let { s2 ->
                _state.value = s2.copy(
                    sortMode = mode,
                    fullComments = all,
                    sortedComments = sorted,
                    sortedDisplayCount = minOf(REVERSE_PAGE_SIZE, sorted.size),
                    isLoadingSort = false
                )
            }
        }
    }

    /** 始终从时间正序的源列表出发排序，保证多次切换结果稳定正确 */
    private fun applySort(comments: List<Comment>, mode: CommentSort): List<Comment> = when (mode) {
        CommentSort.REVERSED -> comments.reversed()
        CommentSort.BY_LIKES -> comments.sortedByDescending { it.lights }  // 稳定排序：同赞数保持时间序
        CommentSort.DEFAULT  -> comments
    }

    fun loadMoreSorted() {
        val s = _state.value as? PostDetailUiState.Success ?: return
        if (s.sortMode == CommentSort.DEFAULT || s.sortedDisplayCount >= s.sortedComments.size) return
        _state.value = s.copy(
            sortedDisplayCount = minOf(s.sortedDisplayCount + REVERSE_PAGE_SIZE, s.sortedComments.size)
        )
    }

    /** 返回上一层 */
    fun popReplies() {
        val s = _state.value as? PostDetailUiState.Success ?: return
        _state.value = s.copy(
            replyStack          = s.replyStack.dropLast(1),
            isLoadingSubReplies = false
        )
    }

    /** 关闭整个回复面板 */
    fun dismissReplies() {
        val s = _state.value as? PostDetailUiState.Success ?: return
        _state.value = s.copy(replyStack = emptyList())
    }

    fun startReply(comment: Comment) {
        val s = _state.value as? PostDetailUiState.Success ?: return
        replyUploadJobs.values.forEach { it.cancel() }
        replyUploadJobs.clear()
        _state.value = s.copy(showReplySheet = true, replyingTo = comment, replyContent = "", replyImages = emptyList(), replyError = null, replySuccess = false)
    }

    fun startMainPostReply() {
        val s = _state.value as? PostDetailUiState.Success ?: return
        replyUploadJobs.values.forEach { it.cancel() }
        replyUploadJobs.clear()
        _state.value = s.copy(showReplySheet = true, replyingTo = null, replyContent = "", replyImages = emptyList(), replyError = null, replySuccess = false)
    }

    fun dismissReply() {
        replyUploadJobs.values.forEach { it.cancel() }
        replyUploadJobs.clear()
        val s = _state.value as? PostDetailUiState.Success ?: return
        _state.value = s.copy(showReplySheet = false, replyingTo = null, replyContent = "", replyImages = emptyList(), replyError = null)
    }

    fun addReplyImage(uri: Uri) {
        val s = _state.value as? PostDetailUiState.Success ?: return
        _state.value = s.copy(replyImages = s.replyImages + ImageItem(uri, ImageItem.Status.Uploading))
        val job = viewModelScope.launch {
            runCatching { postRepo.uploadImageForReply(uri) }
                .onSuccess { fileSrc ->
                    val s2 = _state.value as? PostDetailUiState.Success ?: return@onSuccess
                    _state.value = s2.copy(replyImages = s2.replyImages.map { img ->
                        if (img.uri == uri) img.copy(status = ImageItem.Status.Done, fileSrc = fileSrc) else img
                    })
                }
                .onFailure { e ->
                    val s2 = _state.value as? PostDetailUiState.Success ?: return@onFailure
                    _state.value = s2.copy(replyImages = s2.replyImages.map { img ->
                        if (img.uri == uri) img.copy(status = ImageItem.Status.Error, errorMsg = e.message) else img
                    })
                }
        }
        replyUploadJobs[uri] = job
    }

    fun removeReplyImage(uri: Uri) {
        replyUploadJobs.remove(uri)?.cancel()
        val s = _state.value as? PostDetailUiState.Success ?: return
        _state.value = s.copy(replyImages = s.replyImages.filter { it.uri != uri })
    }

    fun updateReplyContent(text: String) {
        val s = _state.value as? PostDetailUiState.Success ?: return
        _state.value = s.copy(replyContent = text, replyError = null)
    }

    fun submitReply() {
        val s = _state.value as? PostDetailUiState.Success ?: return
        val comment = s.replyingTo   // null = 回复主贴
        val text = s.replyContent.trim()
        val doneImages = s.replyImages.filter { it.status == ImageItem.Status.Done }
        if (text.isEmpty() && doneImages.isEmpty()) {
            _state.value = s.copy(replyError = "请输入回复内容")
            return
        }
        val sig = cookiePrefs.replySignature.trim()
        val textWithSig = if (text.isNotEmpty()) (if (sig.isNotEmpty()) "$text\n$sig" else text) else ""
        val textHtml = if (textWithSig.isNotEmpty()) "<p>$textWithSig</p>" else ""
        val imgHtml  = doneImages.joinToString("") { """<img src="${it.fileSrc}" />""" }
        _state.value = s.copy(isSubmittingReply = true, replyError = null)
        viewModelScope.launch {
            runCatching {
                postRepo.submitReply(
                    tid     = currentTid,
                    fid     = s.post.fid,
                    topicId = s.post.topicId,
                    quoteId = comment?.pid ?: "0",
                    content = textHtml + imgHtml
                )
            }.onSuccess {
                _state.value = (_state.value as? PostDetailUiState.Success)
                    ?.copy(isSubmittingReply = false, replySuccess = true)
                    ?: _state.value
            }.onFailure { e ->
                _state.value = (_state.value as? PostDetailUiState.Success)
                    ?.copy(isSubmittingReply = false, replyError = e.message ?: "回复失败")
                    ?: _state.value
            }
        }
    }

    fun toggleLike(comment: Comment) {
        val s = _state.value as? PostDetailUiState.Success ?: return
        val pid = comment.pid
        if (pid in s.likingPids) return
        val isCurrentlyLiked = pid in s.likedPids

        // 乐观更新
        val optimisticLiked = if (isCurrentlyLiked) s.likedPids - pid else s.likedPids + pid
        _state.value = s.copy(likedPids = optimisticLiked, likingPids = s.likingPids + pid)

        viewModelScope.launch {
            runCatching {
                postRepo.toggleLikeComment(pid, currentTid, s.post.fid, isCurrentlyLiked)
            }.onSuccess {
                val s2 = _state.value as? PostDetailUiState.Success ?: return@onSuccess
                _state.value = s2.copy(likingPids = s2.likingPids - pid)
            }.onFailure {
                // 回滚
                val s2 = _state.value as? PostDetailUiState.Success ?: return@onFailure
                val reverted = if (isCurrentlyLiked) s2.likedPids + pid else s2.likedPids - pid
                _state.value = s2.copy(likedPids = reverted, likingPids = s2.likingPids - pid)
            }
        }
    }

    fun toggleCollect() {
        val s = _state.value as? PostDetailUiState.Success ?: return
        if (s.isCollecting) return
        val newCollected = !s.isCollected
        _state.value = s.copy(isCollected = newCollected, isCollecting = true)
        viewModelScope.launch {
            runCatching {
                postRepo.collectPost(currentTid, s.isCollected)
            }.onSuccess {
                val s2 = _state.value as? PostDetailUiState.Success ?: return@onSuccess
                _state.value = s2.copy(isCollecting = false)
            }.onFailure {
                val s2 = _state.value as? PostDetailUiState.Success ?: return@onFailure
                _state.value = s2.copy(isCollected = !newCollected, isCollecting = false)
            }
        }
    }

    fun recommendPost() {
        val s = _state.value as? PostDetailUiState.Success ?: return
        if (s.isRecommending) return
        val newRecommended = !s.isRecommended
        _state.value = s.copy(isRecommended = newRecommended, isRecommending = true)
        viewModelScope.launch {
            runCatching {
                postRepo.recommendPost(currentTid, s.post.fid, s.isRecommended)
            }.onSuccess {
                val s2 = _state.value as? PostDetailUiState.Success ?: return@onSuccess
                _state.value = s2.copy(isRecommending = false)
            }.onFailure {
                val s2 = _state.value as? PostDetailUiState.Success ?: return@onFailure
                _state.value = s2.copy(isRecommended = !newRecommended, isRecommending = false)
            }
        }
    }

    private fun buildSubRepliesMap(comments: List<Comment>): Map<String, List<Comment>> =
        comments
            .filter { it.quotePid != null }
            .groupBy { it.quotePid!! }

    companion object {
        private const val REVERSE_PAGE_SIZE = 20
    }
}
