package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.HardwareHudBar
import com.example.ui.components.HardwareSettingsSheet
import com.example.ui.components.ModelSelectorSheet
import com.example.ui.components.QuickCommandChips
import com.example.ui.components.StreamingOutputView
import com.example.ui.components.TerminalInputField
import com.example.ui.components.TerminalLogItem
import com.example.ui.theme.TerminalBg

@Composable
fun ConsoleScreen(
    viewModel: ConsoleViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allModels by viewModel.allModels.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val hardwareConfig by viewModel.hardwareConfig.collectAsStateWithLifecycle()
    val hardwareInfo by viewModel.hardwareInfo.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    // Auto-scroll on new logs or streaming
    LaunchedEffect(logs.size, uiState.currentStreamingResponse) {
        if (logs.isNotEmpty() || uiState.currentStreamingResponse.isNotEmpty()) {
            val totalItems = logs.size + (if (uiState.currentStreamingResponse.isNotEmpty()) 1 else 0)
            listState.animateScrollToItem((totalItems - 1).coerceAtLeast(0))
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("console_screen_root"),
        containerColor = TerminalBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(TerminalBg)
        ) {
            // 1. Top HUD Bar
            HardwareHudBar(
                selectedModel = selectedModel,
                config = hardwareConfig,
                liveTokSec = uiState.liveTokensPerSec,
                isGenerating = uiState.isGenerating,
                onOpenModels = { viewModel.toggleModelSelector(true) },
                onOpenSettings = { viewModel.toggleHardwareSettings(true) }
            )

            // 2. Terminal Console Output Logs
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("terminal_logs_list")
            ) {
                items(logs, key = { it.id }) { log ->
                    TerminalLogItem(log = log)
                }

                if (uiState.currentStreamingResponse.isNotEmpty()) {
                    item(key = "streaming_response") {
                        StreamingOutputView(
                            text = uiState.currentStreamingResponse,
                            liveTokensCount = uiState.liveTokensCount,
                            liveTokSec = uiState.liveTokensPerSec,
                            isGpu = hardwareConfig.useGpuAcceleration
                        )
                    }
                }
            }

            // 3. Quick Slash Commands Bar
            QuickCommandChips(
                onCommandClick = { cmd ->
                    viewModel.onInputChange(cmd)
                    viewModel.submitCurrentInput()
                }
            )

            Spacer(modifier = Modifier.height(2.dp))

            // 4. Terminal Command Line Input
            TerminalInputField(
                input = uiState.currentInput,
                isGenerating = uiState.isGenerating,
                onInputChange = { viewModel.onInputChange(it) },
                onSubmit = { viewModel.submitCurrentInput() },
                onAbort = { viewModel.abortInference() }
            )
        }

        // Model Selector Sheet
        if (uiState.showModelSelector) {
            ModelSelectorSheet(
                models = allModels,
                downloadStates = downloadStates,
                onSelectModel = { viewModel.selectModel(it) },
                onStartDownload = { viewModel.startDownload(it) },
                onCancelDownload = { viewModel.cancelDownload(it) },
                onTestFastLocal = { viewModel.testWithFastLocalCheckpoint(it) },
                onDeleteModel = { viewModel.deleteModel(it) },
                onImportFile = { uri, name -> viewModel.importCustomFile(uri, name) },
                onDismiss = { viewModel.toggleModelSelector(false) }
            )
        }

        // Hardware & GPU Settings Sheet
        if (uiState.showHardwareSettings) {
            HardwareSettingsSheet(
                config = hardwareConfig,
                hardwareInfo = hardwareInfo,
                onUpdateConfig = { useGpu, delegate, threads, contextWin, temp ->
                    viewModel.updateHardwareConfig(useGpu, delegate, threads, contextWin, temp)
                },
                onDismiss = { viewModel.toggleHardwareSettings(false) }
            )
        }
    }
}
