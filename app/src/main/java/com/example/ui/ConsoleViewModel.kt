package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LocalAiDatabase
import com.example.data.model.AgentLogEntity
import com.example.data.model.LlmModelEntity
import com.example.data.model.LogRole
import com.example.data.model.ModelStatus
import com.example.data.repository.AgentRepository
import com.example.data.repository.ModelRepository
import com.example.engine.ComputeDelegate
import com.example.engine.DeviceHardwareInfo
import com.example.engine.DownloadProgress
import com.example.engine.HardwareConfig
import com.example.engine.HardwareSpecsProvider
import com.example.engine.InferenceEvent
import com.example.engine.LocalAgentTools
import com.example.engine.LocalInferenceEngine
import com.example.engine.ModelDownloadManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ConsoleUiState(
    val currentInput: String = "",
    val isGenerating: Boolean = false,
    val liveTokensPerSec: Float = 0f,
    val liveTokensCount: Int = 0,
    val currentStreamingResponse: String = "",
    val currentThought: String? = null,
    val currentToolInfo: String? = null,
    val showModelSelector: Boolean = false,
    val showHardwareSettings: Boolean = false,
    val showQuickCommands: Boolean = false,
    val bannerMessage: String? = null
)

class ConsoleViewModel(application: Application) : AndroidViewModel(application) {

    private val db = LocalAiDatabase.getDatabase(application)
    val modelRepository = ModelRepository(db.modelDao(), application)
    val agentRepository = AgentRepository(db.agentDao())

    val hardwareSpecsProvider = HardwareSpecsProvider(application)
    val localTools = LocalAgentTools(application, hardwareSpecsProvider)
    val downloadManager = ModelDownloadManager(application, db.modelDao())
    val inferenceEngine = LocalInferenceEngine(localTools, hardwareSpecsProvider)

    private val currentSessionId = UUID.randomUUID().toString()

    val allModels: StateFlow<List<LlmModelEntity>> = modelRepository.allModels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedModel: StateFlow<LlmModelEntity?> = modelRepository.selectedModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val logs: StateFlow<List<AgentLogEntity>> = agentRepository.getLogsForSession(currentSessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadStates: StateFlow<Map<String, DownloadProgress>> = downloadManager.downloadStates

    private val _hardwareConfig = MutableStateFlow(HardwareConfig())
    val hardwareConfig: StateFlow<HardwareConfig> = _hardwareConfig.asStateFlow()

    private val _hardwareInfo = MutableStateFlow(hardwareSpecsProvider.getHardwareInfo())
    val hardwareInfo: StateFlow<DeviceHardwareInfo> = _hardwareInfo.asStateFlow()

    private val _uiState = MutableStateFlow(ConsoleUiState())
    val uiState: StateFlow<ConsoleUiState> = _uiState.asStateFlow()

    private var activeInferenceJob: Job? = null

    init {
        viewModelScope.launch {
            modelRepository.initializeCatalogIfEmpty()
            refreshHardwareInfo()
            postSystemWelcome()
        }
    }

    fun refreshHardwareInfo() {
        _hardwareInfo.value = hardwareSpecsProvider.getHardwareInfo()
    }

    private suspend fun postSystemWelcome() {
        val hw = _hardwareInfo.value
        val welcomeMsg = buildString {
            appendLine("=== LOCAL LLM AGENT CONSOLE [v1.0-DEV] ===")
            appendLine("Donanım: ${hw.deviceModel} | ${hw.cpuCores} Cores | RAM: ${hw.freeRamMb}/${hw.totalRamMb} MB")
            appendLine("Hızlandırıcı: ${hw.gpuType}")
            appendLine("Vulkan Desteği: ${if (hw.isVulkanSupported) "AKTİF" else "YOK"}")
            appendLine("Tamamen ÇEVRİMDIŞI ve YEREL çalışır. Harici API bağlantısı yoktur.")
            appendLine("Yardım için '/help' yazabilir veya üstteki menüden model seçebilirsiniz.")
        }
        agentRepository.addLog(
            AgentLogEntity(
                sessionId = currentSessionId,
                role = LogRole.SYSTEM,
                content = welcomeMsg
            )
        )
    }

    fun onInputChange(newInput: String) {
        _uiState.value = _uiState.value.copy(currentInput = newInput)
    }

    fun submitCurrentInput() {
        val input = _uiState.value.currentInput.trim()
        if (input.isEmpty() || _uiState.value.isGenerating) return

        _uiState.value = _uiState.value.copy(currentInput = "")

        viewModelScope.launch {
            // Save user log
            agentRepository.addLog(
                AgentLogEntity(
                    sessionId = currentSessionId,
                    role = LogRole.USER,
                    content = input
                )
            )

            // Check if input is a console command
            if (input.startsWith("/")) {
                handleSlashCommand(input)
            } else {
                runInference(input)
            }
        }
    }

    private suspend fun handleSlashCommand(command: String) {
        val parts = command.split(" ", limit = 2)
        when (parts[0].lowercase()) {
            "/help" -> {
                val helpText = buildString {
                    appendLine("KULLANILABİLİR KONSOL KOMUTLARI:")
                    appendLine("  /models       - İndirilen ve mevcut LLM modellerini listeler / seçiciyi açar")
                    appendLine("  /gpu [on|off] - GPU donanım hızlandırmasını açar veya kapatır")
                    appendLine("  /sysinfo      - Detaylı cihaz CPU/GPU/RAM durumunu yazdırır")
                    appendLine("  /bench        - GPU vs CPU tensör çıkarım hız testini başlatır")
                    appendLine("  /tools        - Yerel agent'ın kullanabildiği fonksiyonları listeler")
                    appendLine("  /clear        - Konsol ekranını ve oturum geçmişini temizler")
                    appendLine("  /settings     - Donanım ve çıkarım ayarları panelini açar")
                }
                agentRepository.addLog(
                    AgentLogEntity(sessionId = currentSessionId, role = LogRole.SYSTEM, content = helpText)
                )
            }
            "/models" -> {
                _uiState.value = _uiState.value.copy(showModelSelector = true)
            }
            "/settings" -> {
                _uiState.value = _uiState.value.copy(showHardwareSettings = true)
            }
            "/gpu" -> {
                val arg = parts.getOrNull(1)?.lowercase()
                val current = _hardwareConfig.value.useGpuAcceleration
                val newState = when (arg) {
                    "on", "1", "true" -> true
                    "off", "0", "false" -> false
                    else -> !current
                }
                _hardwareConfig.value = _hardwareConfig.value.copy(useGpuAcceleration = newState)
                val statusText = if (newState) "GPU Hızlandırma: AKTİF (Vulkan / Adreno-Mali Shader)" else "GPU Hızlandırma: KAPALI (CPU Multi-thread Modu)"
                agentRepository.addLog(
                    AgentLogEntity(sessionId = currentSessionId, role = LogRole.SYSTEM, content = statusText)
                )
            }
            "/sysinfo" -> {
                val result = localTools.executeTool("sys_info", "")
                agentRepository.addLog(
                    AgentLogEntity(sessionId = currentSessionId, role = LogRole.SYSTEM, content = result.output)
                )
            }
            "/bench" -> {
                runInference("Yerel donanım benchmark ve hız testi yap")
            }
            "/tools" -> {
                val toolsText = buildString {
                    appendLine("OTONOM YEREL AGENT ARAÇLARI:")
                    localTools.availableTools.forEach { t ->
                        appendLine("• ${t.name}: ${t.description} (Örnek: ${t.usageExample})")
                    }
                }
                agentRepository.addLog(
                    AgentLogEntity(sessionId = currentSessionId, role = LogRole.SYSTEM, content = toolsText)
                )
            }
            "/clear" -> {
                agentRepository.clearSession(currentSessionId)
                postSystemWelcome()
            }
            else -> {
                agentRepository.addLog(
                    AgentLogEntity(sessionId = currentSessionId, role = LogRole.ERROR, content = "Bilinmeyen komut: $command. Yardım için '/help' yazın.")
                )
            }
        }
    }

    fun runInference(prompt: String) {
        activeInferenceJob?.cancel()
        activeInferenceJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGenerating = true,
                liveTokensPerSec = 0f,
                liveTokensCount = 0,
                currentStreamingResponse = "",
                currentThought = null,
                currentToolInfo = null
            )

            val currentModel = selectedModel.value
            val config = _hardwareConfig.value

            inferenceEngine.runInference(prompt, currentModel, config).collect { event ->
                when (event) {
                    is InferenceEvent.Status -> {
                        // Emit live status in console
                        agentRepository.addLog(
                            AgentLogEntity(
                                sessionId = currentSessionId,
                                role = LogRole.SYSTEM,
                                content = "⚙ [SİSTEM] ${event.message}"
                            )
                        )
                    }
                    is InferenceEvent.Thought -> {
                        _uiState.value = _uiState.value.copy(currentThought = event.thought)
                        agentRepository.addLog(
                            AgentLogEntity(
                                sessionId = currentSessionId,
                                role = LogRole.AGENT_THOUGHT,
                                content = event.thought
                            )
                        )
                    }
                    is InferenceEvent.ToolCall -> {
                        _uiState.value = _uiState.value.copy(currentToolInfo = "Araç Çağrılıyor: ${event.toolName}(${event.input})")
                    }
                    is InferenceEvent.ToolResult -> {
                        _uiState.value = _uiState.value.copy(currentToolInfo = null)
                        agentRepository.addLog(
                            AgentLogEntity(
                                sessionId = currentSessionId,
                                role = LogRole.AGENT_TOOL,
                                content = event.output,
                                toolName = event.toolName,
                                executionDurationMs = event.durationMs
                            )
                        )
                    }
                    is InferenceEvent.Token -> {
                        _uiState.value = _uiState.value.copy(
                            currentStreamingResponse = _uiState.value.currentStreamingResponse + event.token,
                            liveTokensCount = event.tokensSoFar,
                            liveTokensPerSec = event.currentTokPerSec
                        )
                    }
                    is InferenceEvent.Complete -> {
                        agentRepository.addLog(
                            AgentLogEntity(
                                sessionId = currentSessionId,
                                role = LogRole.AGENT_RESPONSE,
                                content = event.finalResponse,
                                modelUsed = currentModel?.name,
                                tokensGenerated = event.totalTokens,
                                tokensPerSecond = event.tokensPerSec,
                                isGpuAccelerated = event.isGpuAccelerated,
                                executionDurationMs = event.durationMs
                            )
                        )
                        _uiState.value = _uiState.value.copy(
                            isGenerating = false,
                            currentStreamingResponse = "",
                            currentThought = null,
                            currentToolInfo = null,
                            liveTokensPerSec = event.tokensPerSec
                        )
                    }
                    is InferenceEvent.Error -> {
                        agentRepository.addLog(
                            AgentLogEntity(
                                sessionId = currentSessionId,
                                role = LogRole.ERROR,
                                content = "Hata: ${event.errorMessage}"
                            )
                        )
                        _uiState.value = _uiState.value.copy(
                            isGenerating = false,
                            currentStreamingResponse = "",
                            currentThought = null,
                            currentToolInfo = null
                        )
                    }
                }
            }
        }
    }

    fun abortInference() {
        activeInferenceJob?.cancel()
        activeInferenceJob = null
        _uiState.value = _uiState.value.copy(
            isGenerating = false,
            currentStreamingResponse = "",
            currentThought = null,
            currentToolInfo = null
        )
        viewModelScope.launch {
            agentRepository.addLog(
                AgentLogEntity(
                    sessionId = currentSessionId,
                    role = LogRole.SYSTEM,
                    content = "[İşlem Kullanıcı Tarafından Durduruldu]"
                )
            )
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            modelRepository.selectModel(modelId)
            val model = modelRepository.getModelById(modelId)
            if (model != null) {
                agentRepository.addLog(
                    AgentLogEntity(
                        sessionId = currentSessionId,
                        role = LogRole.SYSTEM,
                        content = "Model Aktif Edildi: ${model.name} (${model.quantization} | ${model.parameterSize})"
                    )
                )
            }
        }
    }

    fun startDownload(model: LlmModelEntity) {
        downloadManager.startDownload(model)
    }

    fun cancelDownload(modelId: String) {
        downloadManager.cancelDownload(modelId)
    }

    fun testWithFastLocalCheckpoint(model: LlmModelEntity) {
        viewModelScope.launch {
            downloadManager.createFastLocalCheckpoint(model)
            modelRepository.selectModel(model.id)
            agentRepository.addLog(
                AgentLogEntity(
                    sessionId = currentSessionId,
                    role = LogRole.SYSTEM,
                    content = "Hızlı Yerel Test Başlatıldı: ${model.name} (${model.quantization}) belleğe alındı. Artık çıkarım yapabilirsiniz!"
                )
            )
        }
    }

    fun deleteModel(model: LlmModelEntity) {
        viewModelScope.launch {
            modelRepository.deleteModelFile(model)
        }
    }

    fun importCustomFile(uri: Uri, fileName: String) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val modelsDir = modelRepository.getModelsDirectory()
                val targetFile = File(modelsDir, fileName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val imported = modelRepository.addCustomModel(
                    name = fileName.removeSuffix(".gguf").removeSuffix(".bin"),
                    file = targetFile
                )
                agentRepository.addLog(
                    AgentLogEntity(
                        sessionId = currentSessionId,
                        role = LogRole.SYSTEM,
                        content = "Özel Model Başarıyla Yüklendi: ${imported.name} (${imported.totalSizeBytes / (1024 * 1024)} MB)"
                    )
                )
            } catch (e: Exception) {
                agentRepository.addLog(
                    AgentLogEntity(
                        sessionId = currentSessionId,
                        role = LogRole.ERROR,
                        content = "Dosya içe aktarma hatası: ${e.localizedMessage}"
                    )
                )
            }
        }
    }

    fun updateHardwareConfig(
        useGpu: Boolean? = null,
        delegate: ComputeDelegate? = null,
        cpuThreads: Int? = null,
        contextWindow: Int? = null,
        temperature: Float? = null
    ) {
        val curr = _hardwareConfig.value
        _hardwareConfig.value = curr.copy(
            useGpuAcceleration = useGpu ?: curr.useGpuAcceleration,
            computeDelegate = delegate ?: curr.computeDelegate,
            cpuThreads = cpuThreads ?: curr.cpuThreads,
            contextWindowSize = contextWindow ?: curr.contextWindowSize,
            temperature = temperature ?: curr.temperature
        )
    }

    fun toggleModelSelector(show: Boolean) {
        _uiState.value = _uiState.value.copy(showModelSelector = show)
    }

    fun toggleHardwareSettings(show: Boolean) {
        _uiState.value = _uiState.value.copy(showHardwareSettings = show)
    }

    fun toggleQuickCommands(show: Boolean) {
        _uiState.value = _uiState.value.copy(showQuickCommands = show)
    }
}
