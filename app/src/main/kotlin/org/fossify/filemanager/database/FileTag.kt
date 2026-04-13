package org.fossify.filemanager.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_tags")
data class FileTag(
    @PrimaryKey
    val filePath: String,
    val tags: String
)
