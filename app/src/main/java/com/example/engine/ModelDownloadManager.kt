package com.example.engine

import android.content.Context
import com.example.data.dao.ModelDao
import com.example.data.model.LlmModelEntity
import com.example.data.model.ModelStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DownloadProgress(
    val modelId: String,
    val progressPct: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedMbPerSec: Float,
    val isDownloading: Boolean = false,
    val error: String? = null
)

class ModelDownloadManager(
    private val context: Context,
    private val modelDao: ModelDao
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val _downloadStates = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadProgress>> = _downloadStates.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    fun startDownload(model: LlmModelEntity) {
        if (downloadJobs[model.id]?.isActive == true) return

        val job = scope.launch {
            val modelsDir = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }
            val outputFile = File(modelsDir, model.fileName)
            val tempFile = File(modelsDir, "${model.fileName}.download")

            try {
                modelDao.updateDownloadStatus(model.id, ModelStatus.DOWNLOADING, 0L, null)
                updateProgress(
                    model.id,
                    DownloadProgress(
                        modelId = model.id,
                        progressPct = 0f,
                        downloadedBytes = 0L,
                        totalBytes = model.totalSizeBytes,
                        speedMbPerSec = 0f,
                        isDownloading = true
                    )
                )

                val request = Request.Builder()
                    .url(model.downloadUrl)
                    .header("User-Agent", "LocalAiAndroidAgent/1.0")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Sunucu hatası: HTTP ${response.code}")
                }

                val body = response.body ?: throw IllegalStateException("Boş yanıt alındı")
                val contentLength = if (body.contentLength() > 0) body.contentLength() else model.totalSizeBytes
                
                var downloaded = 0L
                var lastTime = System.currentTimeMillis()
                var bytesSinceLastTime = 0L

                val inputStream: InputStream = body.byteStream()
                val outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    bytesSinceLastTime += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastTime >= 500) {
                        val durationSec = (now - lastTime) / 1000f
                        val speedMb = (bytesSinceLastTime / (1024f * 1024f)) / durationSec
                        val progress = if (contentLength > 0) (downloaded.toFloat() / contentLength.toFloat()) else 0f

                        updateProgress(
                            model.id,
                            DownloadProgress(
                                modelId = model.id,
                                progressPct = (progress * 100f).coerceIn(0f, 100f),
                                downloadedBytes = downloaded,
                                totalBytes = contentLength,
                                speedMbPerSec = speedMb,
                                isDownloading = true
                            )
                        )
                        lastTime = now
                        bytesSinceLastTime = 0
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                // Rename temp file to final target file
                if (outputFile.exists()) outputFile.delete()
                tempFile.renameTo(outputFile)

                modelDao.updateDownloadStatus(
                    modelId = model.id,
                    status = ModelStatus.DOWNLOADED,
                    downloaded = outputFile.length(),
                    path = outputFile.absolutePath
                )

                updateProgress(
                    model.id,
                    DownloadProgress(
                        modelId = model.id,
                        progressPct = 100f,
                        downloadedBytes = outputFile.length(),
                        totalBytes = outputFile.length(),
                        speedMbPerSec = 0f,
                        isDownloading = false
                    )
                )

            } catch (e: Exception) {
                if (e is CancellationException) {
                    tempFile.delete()
                    modelDao.updateDownloadStatus(model.id, ModelStatus.AVAILABLE, 0L, null)
                    removeProgress(model.id)
                } else {
                    modelDao.updateDownloadStatus(model.id, ModelStatus.ERROR, 0L, null)
                    updateProgress(
                        model.id,
                        DownloadProgress(
                            modelId = model.id,
                            progressPct = 0f,
                            downloadedBytes = 0L,
                            totalBytes = model.totalSizeBytes,
                            speedMbPerSec = 0f,
                            isDownloading = false,
                            error = e.localizedMessage ?: "İndirme hatası oluştu"
                        )
                    )
                }
            } finally {
                downloadJobs.remove(model.id)
            }
        }
        downloadJobs[model.id] = job
    }

    fun cancelDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
    }

    /**
     * Creates a fast local simulated weight checkpoint for instant testing
     * without consuming 1GB+ mobile data.
     */
    suspend fun createFastLocalCheckpoint(model: LlmModelEntity) = withContext(Dispatchers.IO) {
        val modelsDir = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }
        val targetFile = File(modelsDir, model.fileName)
        
        // Write standard GGUF header magic (GGUF: 0x46554747 in little-endian)
        FileOutputStream(targetFile).use { fos ->
            val ggufHeader = byteArrayOf(
                0x47, 0x47, 0x55, 0x46, // "GGUF"
                0x03, 0x00, 0x00, 0x00, // Version 3
                0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 16 tensors
                0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00  // 8 metadata keys
            )
            fos.write(ggufHeader)
            // write padded mock binary weights block
            val padding = ByteArray(1024 * 128) { 0x5A.toByte() }
            fos.write(padding)
        }

        modelDao.updateDownloadStatus(
            modelId = model.id,
            status = ModelStatus.DOWNLOADED,
            downloaded = targetFile.length(),
            path = targetFile.absolutePath
        )
    }

    private fun updateProgress(modelId: String, progress: DownloadProgress) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            put(modelId, progress)
        }
    }

    private fun removeProgress(modelId: String) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            remove(modelId)
        }
    }
}
