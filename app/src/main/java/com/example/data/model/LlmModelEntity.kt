package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ModelStatus {
    AVAILABLE,      // Ready to download
    DOWNLOADING,    // Download in progress
    DOWNLOADED,     // Ready to use locally
    ERROR           // Download failed
}

@Entity(tableName = "llm_models")
data class LlmModelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val fileName: String,
    val parameterSize: String,       // e.g. "1.1B", "135M", "2B"
    val quantization: String,        // e.g. "Q4_K_M", "Q8_0", "FP16"
    val totalSizeBytes: Long,
    val downloadedBytes: Long = 0L,
    val status: ModelStatus = ModelStatus.AVAILABLE,
    val ramRequiredMb: Int,
    val gpuAccelerated: Boolean = true,
    val description: String,
    val downloadUrl: String,
    val localFilePath: String? = null,
    val isSelected: Boolean = false,
    val isCustomImported: Boolean = false
)
