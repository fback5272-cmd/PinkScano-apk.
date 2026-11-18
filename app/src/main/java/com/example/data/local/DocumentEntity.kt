package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val filePath: String,
    val thumbnailPath: String?,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isFavorite: Boolean = false,
    val sourceType: String = "SCAN"
)
