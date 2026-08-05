package com.hdfc.docupload.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hdfc.docupload.domain.model.Resource
import com.hdfc.docupload.domain.repository.DocumentRepository
import com.hdfc.docupload.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker that retries uploading any pending documents once the
 * network constraint is satisfied. Enqueued by [DocumentRepositoryImpl].
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: DocumentRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val appNo = inputData.getString(Constants.KEY_APPLICATION_NUMBER)
            ?: return Result.failure()

        return when (repository.uploadPending(appNo) { /* progress ignored in bg */ }) {
            is Resource.Success -> Result.success()
            is Resource.Error -> Result.retry()
            Resource.Loading -> Result.retry()
        }
    }
}
