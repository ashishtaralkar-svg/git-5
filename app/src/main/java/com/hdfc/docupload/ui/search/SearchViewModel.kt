package com.hdfc.docupload.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hdfc.docupload.domain.model.Resource
import com.hdfc.docupload.domain.repository.ApplicationRepository
import com.hdfc.docupload.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val applicationNumber: String = "",
    val error: String? = null,
    val isLoading: Boolean = false,
    val navigateTo: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val applicationRepository: ApplicationRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val alphanumeric = Regex("^[a-zA-Z0-9]*$")

    fun onApplicationNumberChange(value: String) {
        // Enforce alphanumeric-only input at entry time.
        if (value.matches(alphanumeric)) {
            _uiState.update { it.copy(applicationNumber = value, error = null) }
        } else {
            _uiState.update { it.copy(error = "Only alphanumeric characters are allowed.") }
        }
    }

    fun search() {
        val appNo = _uiState.value.applicationNumber.trim()
        if (appNo.isBlank()) {
            _uiState.update { it.copy(error = "Application Number is required.") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = applicationRepository.searchApplication(appNo)) {
                is Resource.Success ->
                    _uiState.update { it.copy(isLoading = false, navigateTo = result.data) }
                is Resource.Error ->
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    fun onNavigated() = _uiState.update { it.copy(navigateTo = null) }

    fun logout() = authRepository.logout()
}
