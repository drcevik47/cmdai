package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LogRole {
    USER,
    SYSTEM,
    AGENT_THOUGHT,
    AGENT_TOOL,
    AGENT_RESPONSE,
    ERROR
}

@Entity(tableName = "agent_logs")
data class AgentLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val role: LogRole,
    val content: String,
    val toolName: String? = null,
    val toolInput: String? = null,
    val toolOutput: String? = null,
    val modelUsed: String? = null,
    val tokensGenerated: Int = 0,
    val tokensPerSecond: Float = 0.0f,
    val isGpuAccelerated: Boolean = true,
    val executionDurationMs: Long = 0L
)
