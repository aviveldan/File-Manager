package org.fossify.filemanager

import com.github.ajalt.reprint.core.Reprint
import org.fossify.commons.FossifyApp
import org.fossify.filemanager.database.FileMetadataDatabase

class App : FossifyApp() {
    override val isAppLockFeatureAvailable = true

    val fileMetadataDatabase: FileMetadataDatabase by lazy {
        FileMetadataDatabase.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
        Reprint.initialize(this)
    }
}
