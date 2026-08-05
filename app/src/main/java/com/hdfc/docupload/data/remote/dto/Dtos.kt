package com.hdfc.docupload.data.remote.dto

import com.google.gson.annotations.SerializedName

// ---- POST /login ----
data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class LoginResponse(
    @SerializedName("token") val token: String,
    @SerializedName("expiresIn") val expiresIn: Long
)

// ---- POST /searchApplication ----
data class SearchApplicationRequest(
    @SerializedName("applicationNumber") val applicationNumber: String
)

data class SearchApplicationResponse(
    @SerializedName("applicationNumber") val applicationNumber: String,
    @SerializedName("valid") val valid: Boolean,
    @SerializedName("applicantName") val applicantName: String? = null
)

// ---- POST /uploadDocuments ----
data class UploadDocumentResponse(
    @SerializedName("documentId") val documentId: String,
    @SerializedName("status") val status: String
)

// ---- GET /uploadedDocuments ----
data class UploadedDocumentDto(
    @SerializedName("documentId") val documentId: String,
    @SerializedName("fileName") val fileName: String,
    @SerializedName("sizeBytes") val sizeBytes: Long,
    @SerializedName("uploadedAt") val uploadedAt: Long,
    @SerializedName("url") val url: String
)
