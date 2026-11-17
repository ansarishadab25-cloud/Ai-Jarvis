package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandDao {
    @Query("SELECT * FROM command_history ORDER BY timestamp DESC")
    fun getAllCommands(): Flow<List<CommandEntity>>

    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentCommands(limit: Int = 20): Flow<List<CommandEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(command: CommandEntity): Long

    @Delete
    suspend fun deleteCommand(command: CommandEntity)

    @Query("DELETE FROM command_history")
    suspend fun clearAll()
}
