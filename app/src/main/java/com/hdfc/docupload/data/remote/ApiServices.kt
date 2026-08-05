package com.hdfc.docupload.data.remote

import com.hdfc.docupload.data.remote.dto.LoginRequest
import com.hdfc.docupload.data.remote.dto.LoginResponse
import com.hdfc.docupload.data.remote.dto.SearchApplicationRequest
import com.hdfc.docupload.data.remote.dto.SearchApplicationResponse
import com.hdfc.docupload.data.remote.dto.UploadDocumentResponse
import com.hdfc.docupload.data.remote.dto.UploadedDocumentDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Placeholder service interfaces for the future banking REST backend.
 * All traffic is expected over HTTPS (see [BuildConfig.BASE_URL]).
 */
interface AuthApi {
    @POST("login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}

interface ApplicationApi {
    @POST("searchApplication")
    suspend fun searchApplication(
        @Body request: SearchApplicationRequest
    ): SearchApplicationResponse
}

interface DocumentApi {
    @Multipart
    @POST("uploadDocuments")
    suspend fun uploadDocument(
        @Part("applicationNumber") applicationNumber: RequestBody,
        @Part file: MultipartBody.Part
    ): UploadDocumentResponse

    @GET("uploadedDocuments")
    suspend fun getUploadedDocuments(
        @Query("applicationNumber") applicationNumber: String
    ): List<UploadedDocumentDto>
}
