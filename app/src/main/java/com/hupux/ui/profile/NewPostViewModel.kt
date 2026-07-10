package com.hupux.ui.profile

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.repository.PostRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImageItem(
    val uri: Uri,
    val status: Status,
    val fileSrc: String? = null,
    val errorMsg: String? = null
) {
    enum class Status { Uploading, Done, Error }
}

/** 视频帖：一次只允许一个视频 */
data class VideoItem(
    val uri: Uri,
    val status: Status,
    val videoUrl: String? = null,
    val coverUrl: String? = null,
    val objectKey: String? = null,
    val errorMsg: String? = null
) {
    enum class Status { Uploading, Done, Error }
}

data class NewPostUiState(
    val isSubmitting: Boolean   = false,
    val error: String?          = null,
    val success: Boolean        = false,
    val images: List<ImageItem> = emptyList(),
    val video: VideoItem?       = null,
    val creationType: String    = "REPRINT"   // REPRINT=转载, ORIGINAL=原创
)

class NewPostViewModel constructor(
    private val postRepo: PostRepository,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(NewPostUiState())
    val state = _state.asStateFlow()

    private val uploadJobs = mutableMapOf<Uri, Job>()
    private var videoJob: Job? = null

    // ── 视频帖 ────────────────────────────────────────────────
    fun addVideo(uri: Uri) {
        videoJob?.cancel()
        _state.update { it.copy(video = VideoItem(uri, VideoItem.Status.Uploading), error = null) }
        videoJob = viewModelScope.launch {
            runCatching {
                val up    = postRepo.uploadVideo(uri)
                val cover = postRepo.getVideoCover(up.videoUrl)
                Triple(up.videoUrl, cover, up.objectKey)
            }.onSuccess { (videoUrl, cover, objectKey) ->
                _state.update { s ->
                    s.copy(video = s.video?.takeIf { it.uri == uri }?.copy(
                        status = VideoItem.Status.Done,
                        videoUrl = videoUrl, coverUrl = cover, objectKey = objectKey
                    ) ?: s.video)
                }
            }.onFailure { e ->
                Log.e("HupuVideoUpload", "video upload failed for $uri", e)
                _state.update { s ->
                    s.copy(video = s.video?.takeIf { it.uri == uri }?.copy(
                        status = VideoItem.Status.Error, errorMsg = e.message) ?: s.video)
                }
            }
        }
    }

    fun removeVideo() {
        videoJob?.cancel()
        _state.update { it.copy(video = null) }
    }

    fun setCreationType(type: String) {
        _state.update { it.copy(creationType = type) }
    }

    fun submitVideo(topicId: Int, title: String, desc: String) {
        val v = _state.value.video
        if (v == null || v.status != VideoItem.Status.Done ||
            v.videoUrl == null || v.coverUrl == null || v.objectKey == null) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            runCatching {
                postRepo.createVideoThread(
                    topicId, title.trim(), desc.trim(),
                    v.videoUrl, v.coverUrl, v.objectKey,
                    _state.value.creationType, containsAi = 0
                )
            }.onSuccess { _state.value = NewPostUiState(success = true) }
                .onFailure { e ->
                    _state.update { it.copy(isSubmitting = false, error = e.message ?: "发视频帖失败") }
                }
        }
    }

    fun addImage(uri: Uri) {
        _state.update { it.copy(images = it.images + ImageItem(uri, ImageItem.Status.Uploading)) }
        val job = viewModelScope.launch {
            runCatching { postRepo.uploadImage(uri) }
                .onSuccess { fileSrc ->
                    _state.update { s ->
                        s.copy(images = s.images.map { img ->
                            if (img.uri == uri) img.copy(status = ImageItem.Status.Done, fileSrc = fileSrc)
                            else img
                        })
                    }
                }
                .onFailure { e ->
                    Log.e("HupuImageUpload", "upload failed for $uri", e)
                    _state.update { s ->
                        s.copy(images = s.images.map { img ->
                            if (img.uri == uri) img.copy(status = ImageItem.Status.Error, errorMsg = e.message)
                            else img
                        })
                    }
                }
        }
        uploadJobs[uri] = job
    }

    fun removeImage(uri: Uri) {
        uploadJobs.remove(uri)?.cancel()
        _state.update { it.copy(images = it.images.filter { img -> img.uri != uri }) }
    }

    fun submit(topicId: Int, title: String, rawContent: String) {
        val textHtml = rawContent.trim()
            .split("\n")
            .joinToString("") { line ->
                if (line.isEmpty()) "<p><br></p>" else "<p>$line</p>"
            }
            .ifEmpty { "<p><br></p>" }

        val imageHtml = _state.value.images
            .filter { it.status == ImageItem.Status.Done }
            .joinToString("") { """<div data-hupu-node="image"><img src="${it.fileSrc}"></div>""" }
        val content = textHtml + imageHtml

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            runCatching { postRepo.createThread(topicId, title.trim(), content) }
                .onSuccess { _state.value = NewPostUiState(success = true) }
                .onFailure { e ->
                    _state.update { it.copy(isSubmitting = false, error = e.message ?: "发帖失败") }
                }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}
