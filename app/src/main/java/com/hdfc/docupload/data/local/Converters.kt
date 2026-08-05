package com.hdfc.docupload.data.local

import androidx.room.TypeConverter
import com.hdfc.docupload.domain.model.DocumentType
import com.hdfc.docupload.domain.model.UploadStatus

class Converters {
    @TypeConverter
    fun fromDocumentType(value: DocumentType): String = value.name

    @TypeConverter
    fun toDocumentType(value: String): DocumentType = DocumentType.valueOf(value)

    @TypeConverter
    fun fromUploadStatus(value: UploadStatus): String = value.name

    @TypeConverter
    fun toUploadStatus(value: String): UploadStatus = UploadStatus.valueOf(value)
}
