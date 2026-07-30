package com.hdfc.docupload.ui.upload

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hdfc.docupload.domain.model.Document
import com.hdfc.docupload.domain.model.DocumentType
import com.hdfc.docupload.domain.model.Resource
import com.hdfc.docupload.domain.repository.DocumentRepository
import com.hdfc.docupload.navigation.Screen
import com.hdfc.docupload.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class UploadUiState(
    val isUploading: Boolean = false,
    val progress: Float = 0f,
    val showSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val fileUtils: FileUtils,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val applicationNumber: String =
        savedStateHandle.get<String>(Screen.ARG_APP_NO).orEmpty()

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    val pendingDocuments: StateFlow<List<Document>> =
        repository.pendingDocuments(applicationNumber)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uploadedDocuments: StateFlow<List<Document>> =
        repository.uploadedDocuments(applicationNumber)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Handles documents picked from the gallery / file provider. */
    fun addFromGallery(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    runCatching {
                        val name = fileUtils.queryDisplayName(uri)
                        val mime = fileUtils.mimeType(uri)
                        val copied = fileUtils.copyToInternal(uri, name)
                        persist(copied, name, mime, typeForMime(mime))
                    }.onFailure {
                        _uiState.update { s -> s.copy(error = "Unsupported file type.") }
                    }
                }
            }
        }
    }

    /** Handles pages returned by the ML Kit document scanner. */
    fun addScannedPages(pageUris: List<Uri>) {
        if (pageUris.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                pageUris.forEachIndexed { index, uri ->
                    runCatching {
                        val name = "Scan_${System.currentTimeMillis()}_${index + 1}.jpg"
                        val copied = fileUtils.copyToInternal(uri, name)
                        persist(copied, name, "image/jpeg", DocumentType.SCAN)
                    }.onFailure {
                        _uiState.update { s -> s.copy(error = "Document scan failed. Please try again.") }
                    }
                }
            }
        }
    }

    private suspend fun persist(file: File, name: String, mime: String, type: DocumentType) {
        val document = Document(
            applicationNumber = applicationNumber,
            fileName = name,
            localUri = Uri.fromFile(file).toString(),
            mimeType = mime,
            sizeBytes = file.length(),
            type = type
        )
        repository.addDocument(document)
    }

    fun removeDocument(id: Long) {
        viewModelScope.launch { repository.deleteDocument(id) }
    }

    fun upload() {
        if (pendingDocuments.value.isEmpty()) {
            _uiState.update { it.copy(error = "Please add at least one document to upload.") }
            return
        }
        _uiState.update { it.copy(isUploading = true, progress = 0f, error = null) }
        viewModelScope.launch {
            val result = repository.uploadPending(applicationNumber) { progress ->
                _uiState.update { it.copy(progress = progress) }
            }
            when (result) {
                is Resource.Success ->
                    _uiState.update { it.copy(isUploading = false, showSuccess = true) }
                is Resource.Error ->
                    _uiState.update { it.copy(isUploading = false, error = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    fun consumeError() = _uiState.update { it.copy(error = null) }

    fun dismissSuccess() = _uiState.update { it.copy(showSuccess = false) }

    fun reportError(message: String) = _uiState.update { it.copy(error = message) }

    private fun typeForMime(mime: String): DocumentType = when {
        mime.startsWith("image/") -> DocumentType.IMAGE
        mime == "application/pdf" -> DocumentType.PDF
        else -> throw IllegalArgumentException("Unsupported: $mime")
    }
}
