package com.hupux.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewPostUiState(
    val isSubmitting: Boolean = false,
    val error: String?        = null,
    val successTid: Long?     = null
)

@HiltViewModel
class NewPostViewModel @Inject constructor(
    private val postRepo: PostRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NewPostUiState())
    val state = _state.asStateFlow()

    fun submit(topicId: Int, title: String, rawContent: String) {
        val content = rawContent.trim()
            .split("\n")
            .joinToString("") { line ->
                if (line.isEmpty()) "<p><br></p>" else "<p>$line</p>"
            }
            .ifEmpty { "<p><br></p>" }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            runCatching { postRepo.createThread(topicId, title.trim(), content) }
                .onSuccess { tid -> _state.value = NewPostUiState(successTid = tid) }
                .onFailure { e  -> _state.value = NewPostUiState(error = e.message ?: "发帖失败") }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
