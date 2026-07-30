package com.hdfc.docupload.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hdfc.docupload.domain.model.Resource
import com.hdfc.docupload.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val usernameError: String? = null,
    val passwordError: String? = null,
    val generalError: String? = null,
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) = _uiState.update {
        it.copy(username = value, usernameError = null, generalError = null)
    }

    fun onPasswordChange(value: String) = _uiState.update {
        it.copy(password = value, passwordError = null, generalError = null)
    }

    fun login() {
        val state = _uiState.value
        val usernameError = if (state.username.isBlank()) "Username is required." else null
        val passwordError = if (state.password.isBlank()) "Password is required." else null
        if (usernameError != null || passwordError != null) {
            _uiState.update {
                it.copy(usernameError = usernameError, passwordError = passwordError)
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, generalError = null) }
        viewModelScope.launch {
            when (val result = authRepository.login(state.username.trim(), state.password)) {
                is Resource.Success ->
                    _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                is Resource.Error ->
                    _uiState.update { it.copy(isLoading = false, generalError = result.message) }
                Resource.Loading -> Unit
            }
        }
    }
}
