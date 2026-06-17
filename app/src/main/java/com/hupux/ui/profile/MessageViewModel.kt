package com.hupux.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hupux.data.model.MessageItem
import com.hupux.data.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MessageViewModel constructor(
    private val repo: MessageRepository
) : ViewModel() {

    data class TabState(
        val items: List<MessageItem> = emptyList(),
        val isLoading: Boolean = false,
        val hasMore: Boolean = false,
        val error: String? = null,
        val nextPageStr: String = ""
    )

    data class State(
        val selectedTab: Int = 1,
        val tabs: Map<Int, TabState> = mapOf(1 to TabState(), 2 to TabState(), 3 to TabState())
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { loadTab(1) }

    fun selectTab(tabKey: Int) {
        _state.value = _state.value.copy(selectedTab = tabKey)
        val tab = _state.value.tabs[tabKey] ?: TabState()
        if (tab.items.isEmpty() && !tab.isLoading) loadTab(tabKey)
    }

    fun loadMore() {
        val tabKey = _state.value.selectedTab
        val tab = _state.value.tabs[tabKey] ?: return
        if (tab.isLoading || !tab.hasMore) return
        loadTab(tabKey, tab.nextPageStr)
    }

    fun refresh() {
        val tabKey = _state.value.selectedTab
        _state.value = _state.value.copy(
            tabs = _state.value.tabs + (tabKey to TabState())
        )
        loadTab(tabKey)
    }

    private fun loadTab(tabKey: Int, pageStr: String? = null) {
        viewModelScope.launch {
            updateTab(tabKey) { it.copy(isLoading = true, error = null) }
            try {
                val page = repo.fetchMessages(tabKey, pageStr)
                updateTab(tabKey) { tab ->
                    tab.copy(
                        items       = if (pageStr == null) page.items else tab.items + page.items,
                        hasMore     = page.hasNextPage,
                        nextPageStr = page.nextPageStr,
                        isLoading   = false
                    )
                }
            } catch (e: Exception) {
                updateTab(tabKey) { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun updateTab(tabKey: Int, update: (TabState) -> TabState) {
        val current = _state.value.tabs[tabKey] ?: TabState()
        _state.value = _state.value.copy(
            tabs = _state.value.tabs + (tabKey to update(current))
        )
    }
}
