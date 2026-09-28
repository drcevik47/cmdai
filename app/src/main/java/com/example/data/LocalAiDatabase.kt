package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.AgentDao
import com.example.data.dao.ModelDao
import com.example.data.model.AgentLogEntity
import com.example.data.model.LlmModelEntity

@Database(
    entities = [LlmModelEntity::class, AgentLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LocalAiDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun agentDao(): AgentDao

    companion object {
        @Volatile
        private var INSTANCE: LocalAiDatabase? = null

        fun getDatabase(context: Context): LocalAiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LocalAiDatabase::class.java,
                    "local_ai_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
