package org.fossify.filemanager.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface FileTagDao {
    @Upsert
    suspend fun insertOrUpdateTags(fileTag: FileTag)

    @Query("SELECT tags FROM file_tags WHERE filePath = :filePath")
    suspend fun getTagsForPath(filePath: String): String?

    @Query("DELETE FROM file_tags WHERE filePath = :filePath")
    suspend fun deleteTags(filePath: String)

    @Query("UPDATE file_tags SET filePath = :newPath WHERE filePath = :oldPath")
    suspend fun updatePath(oldPath: String, newPath: String)

    @Query("SELECT * FROM file_tags WHERE filePath IN (:paths)")
    suspend fun getTagsForPaths(paths: List<String>): List<FileTag>

    @Query(
        "UPDATE file_tags SET filePath = :newParent || substr(filePath, length(:oldParent) + 1)" +
            " WHERE filePath = :oldParent OR filePath LIKE :oldParent || '/%'"
    )
    suspend fun updateParentPath(oldParent: String, newParent: String)
}
