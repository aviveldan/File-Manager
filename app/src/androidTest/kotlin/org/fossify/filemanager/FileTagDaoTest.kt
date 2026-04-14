package org.fossify.filemanager

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.fossify.filemanager.database.FileMetadataDatabase
import org.fossify.filemanager.database.FileTag
import org.fossify.filemanager.database.FileTagDao
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class FileTagDaoTest {

    private lateinit var database: FileMetadataDatabase
    private lateinit var dao: FileTagDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, FileMetadataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.fileTagDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveTags() = runBlocking {
        dao.insertOrUpdateTags(FileTag("/storage/emulated/0/Documents/report.pdf", "work, finance"))
        val tags = dao.getTagsForPath("/storage/emulated/0/Documents/report.pdf")
        assertEquals("work, finance", tags)
    }

    @Test
    fun getTagsForNonExistentPathReturnsNull() = runBlocking {
        val tags = dao.getTagsForPath("/no/such/file.txt")
        assertNull(tags)
    }

    @Test
    fun upsertOverwritesExistingTags() = runBlocking {
        val path = "/storage/emulated/0/photo.jpg"
        dao.insertOrUpdateTags(FileTag(path, "vacation"))
        dao.insertOrUpdateTags(FileTag(path, "vacation, beach"))
        val tags = dao.getTagsForPath(path)
        assertEquals("vacation, beach", tags)
    }

    @Test
    fun deleteRemovesTags() = runBlocking {
        val path = "/storage/emulated/0/secret.txt"
        dao.insertOrUpdateTags(FileTag(path, "private"))
        dao.deleteTags(path)
        assertNull(dao.getTagsForPath(path))
    }

    @Test
    fun deleteNonExistentPathDoesNotThrow() = runBlocking {
        dao.deleteTags("/does/not/exist.txt")
    }

    @Test
    fun updatePathRenamesSingleFile() = runBlocking {
        val oldPath = "/storage/emulated/0/old_name.txt"
        val newPath = "/storage/emulated/0/new_name.txt"
        dao.insertOrUpdateTags(FileTag(oldPath, "important"))

        dao.updatePath(oldPath, newPath)

        assertNull(dao.getTagsForPath(oldPath))
        assertEquals("important", dao.getTagsForPath(newPath))
    }

    @Test
    fun getTagsForPathsBatchQuery() = runBlocking {
        val fileA = FileTag("/a/file1.txt", "tag1")
        val fileB = FileTag("/a/file2.txt", "tag2")
        val fileC = FileTag("/b/file3.txt", "tag3")
        dao.insertOrUpdateTags(fileA)
        dao.insertOrUpdateTags(fileB)
        dao.insertOrUpdateTags(fileC)

        val results = dao.getTagsForPaths(listOf("/a/file1.txt", "/b/file3.txt", "/x/missing.txt"))
        assertEquals(2, results.size)
        assertTrue(results.any { it.filePath == "/a/file1.txt" && it.tags == "tag1" })
        assertTrue(results.any { it.filePath == "/b/file3.txt" && it.tags == "tag3" })
    }

    @Test
    fun getTagsForPathsEmptyListReturnsEmpty() = runBlocking {
        val results = dao.getTagsForPaths(emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun updateParentPathRenamesDirectoryAndChildren() = runBlocking {
        val parent = "/storage/emulated/0/OldFolder"
        dao.insertOrUpdateTags(FileTag(parent, "folder-tag"))
        dao.insertOrUpdateTags(FileTag("$parent/doc.txt", "doc-tag"))
        dao.insertOrUpdateTags(FileTag("$parent/sub/image.png", "img-tag"))
        // Unrelated file that should NOT be affected
        dao.insertOrUpdateTags(FileTag("/storage/emulated/0/OldFolderExtra/note.txt", "extra-tag"))

        val newParent = "/storage/emulated/0/NewFolder"
        dao.updateParentPath(parent, newParent)

        // Renamed entries
        assertEquals("folder-tag", dao.getTagsForPath(newParent))
        assertEquals("doc-tag", dao.getTagsForPath("$newParent/doc.txt"))
        assertEquals("img-tag", dao.getTagsForPath("$newParent/sub/image.png"))

        // Old paths no longer exist
        assertNull(dao.getTagsForPath(parent))
        assertNull(dao.getTagsForPath("$parent/doc.txt"))
        assertNull(dao.getTagsForPath("$parent/sub/image.png"))

        // Unrelated file is untouched
        assertEquals("extra-tag", dao.getTagsForPath("/storage/emulated/0/OldFolderExtra/note.txt"))
    }

    @Test
    fun updateParentPathBoundaryDoesNotMatchPrefix() = runBlocking {
        // "OldDir" should NOT match "OldDirectory" — boundary is enforced by '/'
        dao.insertOrUpdateTags(FileTag("/a/OldDir", "dir-tag"))
        dao.insertOrUpdateTags(FileTag("/a/OldDir/child.txt", "child-tag"))
        dao.insertOrUpdateTags(FileTag("/a/OldDirectory/other.txt", "other-tag"))

        dao.updateParentPath("/a/OldDir", "/a/NewDir")

        assertEquals("dir-tag", dao.getTagsForPath("/a/NewDir"))
        assertEquals("child-tag", dao.getTagsForPath("/a/NewDir/child.txt"))
        // Must NOT be renamed
        assertEquals("other-tag", dao.getTagsForPath("/a/OldDirectory/other.txt"))
    }
}
