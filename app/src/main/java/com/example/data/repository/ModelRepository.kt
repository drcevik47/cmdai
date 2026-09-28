package com.example.data.repository

import android.content.Context
import com.example.data.dao.ModelDao
import com.example.data.model.LlmModelEntity
import com.example.data.model.ModelCatalog
import com.example.data.model.ModelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File

class ModelRepository(
    private val modelDao: ModelDao,
    private val context: Context
) {
    val allModels: Flow<List<LlmModelEntity>> = modelDao.getAllModels()
    val selectedModel: Flow<LlmModelEntity?> = modelDao.getSelectedModel()

    suspend fun initializeCatalogIfEmpty() = withContext(Dispatchers.IO) {
        val existing = modelDao.getAllModels().firstOrNull()
        if (existing.isNullOrEmpty()) {
            val catalog = ModelCatalog.getInitialModels()
            // Check if any model already exists in local files
            val modelsDir = getModelsDirectory()
            val initialized = catalog.map { model ->
                val targetFile = File(modelsDir, model.fileName)
                if (targetFile.exists() && targetFile.length() > 0) {
                    model.copy(
                        status = ModelStatus.DOWNLOADED,
                        downloadedBytes = targetFile.length(),
                        localFilePath = targetFile.absolutePath
                    )
                } else {
                    model
                }
            }
            modelDao.insertModels(initialized)
        }
    }

    suspend fun selectModel(modelId: String) = withContext(Dispatchers.IO) {
        modelDao.setSelectedModel(modelId)
    }

    suspend fun getModelById(modelId: String): LlmModelEntity? = withContext(Dispatchers.IO) {
        modelDao.getModelById(modelId)
    }

    suspend fun updateModel(model: LlmModelEntity) = withContext(Dispatchers.IO) {
        modelDao.updateModel(model)
    }

    suspend fun addCustomModel(
        name: String,
        file: File,
        parameterSize: String = "Custom",
        quantization: String = "GGUF"
    ): LlmModelEntity = withContext(Dispatchers.IO) {
        val id = "custom_${System.currentTimeMillis()}"
        val entity = LlmModelEntity(
            id = id,
            name = name,
            fileName = file.name,
            parameterSize = parameterSize,
            quantization = quantization,
            totalSizeBytes = file.length(),
            downloadedBytes = file.length(),
            status = ModelStatus.DOWNLOADED,
            ramRequiredMb = (file.length() / (1024 * 1024) * 1.3).toInt().coerceAtLeast(300),
            gpuAccelerated = true,
            description = "Kullanıcı tarafından yüklenen yerel GGUF / ONNX / model ağırlığı.",
            downloadUrl = "",
            localFilePath = file.absolutePath,
            isSelected = true,
            isCustomImported = true
        )
        modelDao.insertModel(entity)
        modelDao.setSelectedModel(id)
        entity
    }

    suspend fun deleteModelFile(model: LlmModelEntity) = withContext(Dispatchers.IO) {
        model.localFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
        if (model.isCustomImported) {
            modelDao.deleteModel(model.id)
        } else {
            modelDao.updateDownloadStatus(
                modelId = model.id,
                status = ModelStatus.AVAILABLE,
                downloaded = 0L,
                path = null
            )
        }
    }

    fun getModelsDirectory(): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}
