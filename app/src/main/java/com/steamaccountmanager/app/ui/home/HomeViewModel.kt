package com.steamaccountmanager.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamaccountmanager.app.data.repository.AccountRepository
import com.steamaccountmanager.app.domain.model.Account
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val accounts: List<Account> = emptyList(),
    val isLoading: Boolean = true,
)

class HomeViewModel(private val accountRepository: AccountRepository) : ViewModel() {

    private val _localOrder = MutableStateFlow<List<Account>?>(null)

    val uiState: StateFlow<HomeUiState> = accountRepository.observeAccounts()
        .let { flow ->
            kotlinx.coroutines.flow.combine(flow, _localOrder) { fromDb, localOverride ->
                HomeUiState(accounts = localOverride ?: fromDb, isLoading = false)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Optimistic local reorder while dragging; committed to Room on drag end. */
    fun onDragReorder(newOrder: List<Account>) {
        _localOrder.value = newOrder
    }

    fun commitReorder(orderedIds: List<String>) {
        viewModelScope.launch {
            accountRepository.reorder(orderedIds)
            _localOrder.value = null
        }
    }
}
