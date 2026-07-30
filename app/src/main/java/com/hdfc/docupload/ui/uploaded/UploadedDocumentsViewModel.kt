package com.hdfc.docupload.ui.uploaded

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hdfc.docupload.domain.model.Document
import com.hdfc.docupload.domain.repository.DocumentRepository
import com.hdfc.docupload.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UploadedDocumentsViewModel @Inject constructor(
    private val repository: DocumentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val applicationNumber: String =
        savedStateHandle.get<String>(Screen.ARG_APP_NO).orEmpty()

    val documents: StateFlow<List<Document>> =
        repository.uploadedDocuments(applicationNumber)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteDocument(id) }
    }
}
