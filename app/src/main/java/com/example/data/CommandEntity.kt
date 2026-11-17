package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_history")
data class CommandEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val query: String,
    val response: String,
    val intentType: String,
    val language: String,
    val status: String,
    val timestamp: Long = System.currentTimeMillis()
)
