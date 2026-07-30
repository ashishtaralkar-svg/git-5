package com.hdfc.docupload.domain.repository

import com.hdfc.docupload.domain.model.Document
import com.hdfc.docupload.domain.model.Resource
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Validates credentials and, on success, persists a session token. */
    suspend fun login(username: String, password: String): Resource<Unit>

    /** Clears the stored session/token. */
    fun logout()

    /** Whether a valid session currently exists. */
    fun isLoggedIn(): Boolean
}

interface ApplicationRepository {
    /** Verifies an application number exists. Currently always succeeds. */
    suspend fun searchApplication(applicationNumber: String): Resource<String>
}

interface DocumentRepository {
    /** Persists a newly-added (pending) document locally. Returns its local id. */
    suspend fun addDocument(document: Document): Long

    /** Removes a document (local + remote reference) by id. */
    suspend fun deleteDocument(id: Long)

    /** Stream of pending (not-yet-uploaded) documents for an application. */
    fun pendingDocuments(applicationNumber: String): Flow<List<Document>>

    /** Stream of already-uploaded documents for an application. */
    fun uploadedDocuments(applicationNumber: String): Flow<List<Document>>

    /**
     * Uploads all pending documents for the application. Emits progress in the
     * range 0f..1f as a [Resource.Loading] surrogate is not expressive enough,
     * so progress is delivered via [onProgress].
     */
    suspend fun uploadPending(
        applicationNumber: String,
        onProgress: (Float) -> Unit
    ): Resource<Unit>

    /** Enqueues a background (WorkManager) retry for offline uploads. */
    fun scheduleRetry(applicationNumber: String)
}
