package com.example.data.repository

import com.example.data.dao.AgentDao
import com.example.data.model.AgentLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AgentRepository(
    private val agentDao: AgentDao
) {
    fun getLogsForSession(sessionId: String): Flow<List<AgentLogEntity>> {
        return agentDao.getLogsForSession(sessionId)
    }

    suspend fun addLog(log: AgentLogEntity): Long = withContext(Dispatchers.IO) {
        agentDao.insertLog(log)
    }

    suspend fun clearSession(sessionId: String) = withContext(Dispatchers.IO) {
        agentDao.clearSession(sessionId)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        agentDao.clearAllLogs()
    }
}
