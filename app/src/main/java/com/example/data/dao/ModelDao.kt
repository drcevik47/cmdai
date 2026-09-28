package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.LlmModelEntity
import com.example.data.model.ModelStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM llm_models ORDER BY totalSizeBytes ASC")
    fun getAllModels(): Flow<List<LlmModelEntity>>

    @Query("SELECT * FROM llm_models WHERE isSelected = 1 LIMIT 1")
    fun getSelectedModel(): Flow<LlmModelEntity?>

    @Query("SELECT * FROM llm_models WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: String): LlmModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<LlmModelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: LlmModelEntity)

    @Update
    suspend fun updateModel(model: LlmModelEntity)

    @Query("UPDATE llm_models SET isSelected = CASE WHEN id = :modelId THEN 1 ELSE 0 END")
    suspend fun setSelectedModel(modelId: String)

    @Query("UPDATE llm_models SET status = :status, downloadedBytes = :downloaded, localFilePath = :path WHERE id = :modelId")
    suspend fun updateDownloadStatus(modelId: String, status: ModelStatus, downloaded: Long, path: String?)

    @Query("DELETE FROM llm_models WHERE id = :modelId")
    suspend fun deleteModel(modelId: String)
}
