package com.hupux.ui.profile

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImageItem(
    val uri: Uri,
    val status: Status,
    val fileSrc: String? = null,
    val errorMsg: String? = null
) {
    enum class Status { Uploading, Done, Error }
}

data class NewPostUiState(
    val isSubmitting: Boolean   = false,
    val error: String?          = null,
    val success: Boolean        = false,
    val images: List<ImageItem> = emptyList()
)

@HiltViewModel
class NewPostViewModel @Inject constructor(
    private val postRepo: PostRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(NewPostUiState())
    val state = _state.asStateFlow()

    private val uploadJobs = mutableMapOf<Uri, Job>()

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
