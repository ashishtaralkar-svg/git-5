package com.hdfc.docupload.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hdfc.docupload.domain.model.UploadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity): Long

    @Update
    suspend fun update(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: Long): DocumentEntity?

    @Query(
        "SELECT * FROM documents WHERE applicationNumber = :appNo AND status != :uploaded ORDER BY createdAt ASC"
    )
    fun observePending(
        appNo: String,
        uploaded: UploadStatus = UploadStatus.UPLOADED
    ): Flow<List<DocumentEntity>>

    @Query(
        "SELECT * FROM documents WHERE applicationNumber = :appNo AND status = :uploaded ORDER BY createdAt DESC"
    )
    fun observeUploaded(
        appNo: String,
        uploaded: UploadStatus = UploadStatus.UPLOADED
    ): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE applicationNumber = :appNo AND status != :uploaded")
    suspend fun getPendingOnce(
        appNo: String,
        uploaded: UploadStatus = UploadStatus.UPLOADED
    ): List<DocumentEntity>

    @Query("UPDATE documents SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: UploadStatus)
}
