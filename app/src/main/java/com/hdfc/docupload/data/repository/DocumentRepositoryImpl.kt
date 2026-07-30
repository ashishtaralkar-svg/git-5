package com.hdfc.docupload.data.repository

import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hdfc.docupload.data.local.DocumentDao
import com.hdfc.docupload.data.local.toDomain
import com.hdfc.docupload.data.local.toEntity
import com.hdfc.docupload.domain.model.Document
import com.hdfc.docupload.domain.model.Resource
import com.hdfc.docupload.domain.model.UploadStatus
import com.hdfc.docupload.domain.repository.DocumentRepository
import com.hdfc.docupload.util.Constants
import com.hdfc.docupload.util.FileUtils
import com.hdfc.docupload.util.ImageCompressor
import com.hdfc.docupload.util.NetworkMonitor
import com.hdfc.docupload.worker.UploadWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val dao: DocumentDao,
    private val fileUtils: FileUtils,
    private val compressor: ImageCompressor,
    private val networkMonitor: NetworkMonitor,
    private val workManager: WorkManager
) : DocumentRepository {

    override suspend fun addDocument(document: Document): Long =
        dao.insert(document.toEntity())

    override suspend fun deleteDocument(id: Long) {
        dao.getById(id)?.let { fileUtils.delete(it.localUri) }
        dao.deleteById(id)
    }

    override fun pendingDocuments(applicationNumber: String): Flow<List<Document>> =
        dao.observePending(applicationNumber).map { list -> list.map { it.toDomain() } }

    override fun uploadedDocuments(applicationNumber: String): Flow<List<Document>> =
        dao.observeUploaded(applicationNumber).map { list -> list.map { it.toDomain() } }

    override suspend fun uploadPending(
        applicationNumber: String,
        onProgress: (Float) -> Unit
    ): Resource<Unit> {
        val pending = dao.getPendingOnce(applicationNumber)
        if (pending.isEmpty()) return Resource.Success(Unit)

        if (!networkMonitor.isOnline()) {
            // Persist as pending and let WorkManager retry when connectivity returns.
            scheduleRetry(applicationNumber)
            return Resource.Error("No internet connection.")
        }

        val total = pending.size
        pending.forEachIndexed { index, entity ->
            dao.updateStatus(entity.id, UploadStatus.UPLOADING)
            try {
                // Compress images before "upload" (PDFs pass through untouched).
                val localFile = fileFromUri(entity.localUri)
                localFile?.let { compressor.compress(it, entity.mimeType) }

                // Simulated network transfer. Replace with DocumentApi.uploadDocument().
                delay(700)

                dao.update(
                    entity.copy(
                        status = UploadStatus.UPLOADED,
                        remoteId = "srv_${entity.id}"
                    )
                )
            } catch (e: Exception) {
                dao.updateStatus(entity.id, UploadStatus.FAILED)
                scheduleRetry(applicationNumber)
                return Resource.Error("Upload failed. Documents saved for retry.", e)
            }
            onProgress((index + 1) / total.toFloat())
        }
        return Resource.Success(Unit)
    }

    override fun scheduleRetry(applicationNumber: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .setInputData(
                Data.Builder()
                    .putString(Constants.KEY_APPLICATION_NUMBER, applicationNumber)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(
            "${Constants.WORK_UPLOAD_RETRY}_$applicationNumber",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun fileFromUri(uriString: String): File? = runCatching {
        val uri = Uri.parse(uriString)
        uri.path?.let { File(it) }?.takeIf { it.exists() }
    }.getOrNull()
}
