package com.hdfc.docupload.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hdfc.docupload.domain.model.Document
import com.hdfc.docupload.domain.model.DocumentType
import com.hdfc.docupload.domain.model.UploadStatus

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val applicationNumber: String,
    val fileName: String,
    val localUri: String,
    val mimeType: String,
    val sizeBytes: Long,
    val type: DocumentType,
    val status: UploadStatus,
    val createdAt: Long,
    val remoteId: String?
)

fun DocumentEntity.toDomain(): Document = Document(
    id = id,
    applicationNumber = applicationNumber,
    fileName = fileName,
    localUri = localUri,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    type = type,
    status = status,
    createdAt = createdAt,
    remoteId = remoteId
)

fun Document.toEntity(): DocumentEntity = DocumentEntity(
    id = id,
    applicationNumber = applicationNumber,
    fileName = fileName,
    localUri = localUri,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    type = type,
    status = status,
    createdAt = createdAt,
    remoteId = remoteId
)
