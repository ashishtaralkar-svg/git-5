package com.hdfc.docupload.data.repository

import com.hdfc.docupload.domain.model.Resource
import com.hdfc.docupload.domain.repository.ApplicationRepository
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Demo implementation: treats every non-blank application number as valid.
 * Replace with an [ApplicationApi] call for real verification.
 */
@Singleton
class ApplicationRepositoryImpl @Inject constructor() : ApplicationRepository {

    override suspend fun searchApplication(applicationNumber: String): Resource<String> {
        delay(500)
        return Resource.Success(applicationNumber.trim())
    }
}
