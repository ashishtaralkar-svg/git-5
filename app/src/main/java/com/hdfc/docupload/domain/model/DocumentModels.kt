package com.hdfc.docupload.domain.model

/** Source/kind of a document. */
enum class DocumentType { IMAGE, PDF, SCAN }

/** Lifecycle of an individual document with respect to the backend. */
enum class UploadStatus { PENDING, UPLOADING, UPLOADED, FAILED }

/** Colour filter applied to a scanned page (mirrors ML Kit scanner modes). */
enum class ScanFilter { COLOR, GRAYSCALE, ORIGINAL }

/**
 * Domain representation of a document attached to an application.
 *
 * @param id          Stable local identifier (matches the Room primary key).
 * @param applicationNumber The application this document belongs to.
 * @param fileName    Display name.
 * @param localUri    Content/File uri of the local copy.
 * @param mimeType    e.g. image/jpeg, application/pdf.
 * @param sizeBytes   File size in bytes.
 * @param type        Whether it originated from gallery, pdf or scanner.
 * @param status      Upload lifecycle state.
 * @param createdAt   Epoch millis when the document was added.
 * @param remoteId    Server id assigned after a successful upload (nullable).
 */
data class Document(
    val id: Long = 0,
    val applicationNumber: String,
    val fileName: String,
    val localUri: String,
    val mimeType: String,
    val sizeBytes: Long,
    val type: DocumentType,
    val status: UploadStatus = UploadStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val remoteId: String? = null
)
