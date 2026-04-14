package org.fossify.filemanager.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FileTag::class], version = 1, exportSchema = false)
abstract class FileMetadataDatabase : RoomDatabase() {
    abstract fun fileTagDao(): FileTagDao

    companion object {
        @Volatile
        private var instance: FileMetadataDatabase? = null

        fun getInstance(context: Context): FileMetadataDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FileMetadataDatabase::class.java,
                    "file_metadata.db"
                ).build().also { instance = it }
            }
        }
    }
}
