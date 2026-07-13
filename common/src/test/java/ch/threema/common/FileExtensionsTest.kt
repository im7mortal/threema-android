package ch.threema.common

import ch.threema.testhelpers.createTempDirectory
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileExtensionsTest {
    @Test
    fun `clear a directory recursively`() {
        val root = createTempDirectory("root")
        val fileA = File(root, "A")
        fileA.createNewFile()
        val fileB = File(root, "B")
        fileB.createNewFile()
        val directory1 = File(root, "1")
        directory1.mkdir()
        val directory2 = File(root, "2")
        directory2.mkdir()
        val file1A = File(directory1, "1A")
        file1A.createNewFile()
        val directory11 = File(directory1, "11")
        directory11.mkdir()

        root.clearDirectoryRecursively()

        assertTrue(root.exists())
        assertFalse(fileA.exists())
        assertFalse(fileB.exists())
        assertFalse(fileB.exists())
        assertFalse(directory1.exists())
        assertFalse(directory2.exists())
        assertFalse(file1A.exists())
        assertFalse(directory11.exists())
    }

    @Test
    fun `clear a directory non-recursively`() {
        val root = createTempDirectory("root")
        val fileA = File(root, "A")
        fileA.createNewFile()
        val fileB = File(root, "B")
        fileB.createNewFile()
        val directory1 = File(root, "1")
        directory1.mkdir()
        val directory2 = File(root, "2")
        directory2.mkdir()
        val file1A = File(directory1, "1A")
        file1A.createNewFile()
        val directory11 = File(directory1, "11")
        directory11.mkdir()

        root.clearDirectoryNonRecursively()

        assertTrue(root.exists())
        assertFalse(fileA.exists())
        assertFalse(fileB.exists())
        assertFalse(fileB.exists())
        assertTrue(directory1.exists())
        assertTrue(directory2.exists())
        assertTrue(file1A.exists())
        assertTrue(directory11.exists())
    }

    @Test
    fun `get total size of directory`() {
        val root = createTempDirectory("root")
        val fileA = File(root, "A")
        fileA.writeText("Hello") // size 5
        val fileB = File(root, "B")
        fileB.writeText("World") // size 5
        val directory1 = File(root, "1")
        directory1.mkdir()
        val directory2 = File(root, "2")
        directory2.mkdir()
        val file1A = File(directory1, "1A")
        file1A.writeText("\uD83D\uDC08") // size 4
        val file1B = File(directory1, "1A")
        file1B.createNewFile() // size 0
        val directory11 = File(directory1, "11")
        directory11.mkdir()

        val totalSize = root.getTotalDirectorySize()

        assertEquals(14.bytes, totalSize)
    }

    @Test
    fun `get total size of non-existing directory`() {
        val root = createTempDirectory("root")
        val directory = File(root, "A")

        assertEquals(0.bytes, directory.getTotalDirectorySize())
    }

    @Test
    fun `cannot get total directory size of file`() {
        val root = createTempDirectory("root")
        val file = File(root, "A")
        file.createNewFile()

        assertFailsWith<IllegalArgumentException> {
            file.getTotalDirectorySize()
        }
    }

    @Test
    fun `delete a file securely`() {
        val trashDirectory = createTempDirectory()
        val file = File.createTempFile("test", "test")
        file.writeText("Hello World")
        assertTrue(file.exists())

        file.deleteSecurely(trashDirectory)

        assertFalse(file.exists())
        assertContentEquals(emptyArray<File>(), trashDirectory.listFiles())
    }

    @Test
    fun `delete a file securely that does not exist`() {
        val file = File.createTempFile("test", "test")
        file.delete()
        assertFalse(file.exists())

        file.deleteSecurely(createTempDirectory())
        assertFalse(file.exists())
    }

    @Test
    fun `cannot delete a directory securely`() {
        val directory = createTempDirectory("root")

        assertFailsWith<IOException> {
            directory.deleteSecurely(createTempDirectory())
        }
    }

    @Test
    fun `deleteOrThrow deletes files`() {
        val directory = createTempDirectory()
        val file = File(directory, "some-file")
        file.createNewFile()

        file.deleteOrThrow()

        assertFalse(file.exists())
    }

    @Test
    fun `deleteOrThrow throws if the file can not be deleted`() {
        val directory = createTempDirectory()
        File(directory, "some-file").createNewFile()

        assertFailsWith<IOException> {
            directory.deleteOrThrow()
        }
    }

    @Test
    fun `deleteOrThrow does not throw if the file already does not exist`() {
        val directory = createTempDirectory()
        File(directory, "does-not-exist").deleteOrThrow()
    }

    @Test
    fun `renameOrThrow renames files`() {
        val directory = createTempDirectory()
        val file = File(directory, "A")
        file.createNewFile()

        val renamedFile = File(directory, "B")
        file.renameOrThrow(renamedFile)

        assertFalse(file.exists())
        assertTrue(renamedFile.exists())
    }

    @Test
    fun `renameOrThrow throws if file does not exist`() {
        val directory = createTempDirectory()
        val file = File(directory, "A")

        assertFailsWith<IOException> {
            file.renameOrThrow(File(directory, "B"))
        }
    }

    @Test
    fun `get file extension with dot`() {
        val directory = createTempDirectory()
        val fileWithoutExtension = File(directory, "no-extension")
        assertEquals("", fileWithoutExtension.extensionIncludingDot)

        val fileWithExtension = File(directory, "extension.pdf")
        assertEquals(".pdf", fileWithExtension.extensionIncludingDot)
    }

    @Test
    fun `get unique file`() {
        val directory = createTempDirectory()
        val file1 = directory.getUniqueFile(fileName = "my test")
        assertEquals(File(directory, "my test"), file1)

        file1.createNewFile()
        val file2 = directory.getUniqueFile(fileName = "my test")
        assertEquals(File(directory, "my test (1)"), file2)

        file2.createNewFile()
        val file3 = directory.getUniqueFile(fileName = "my test")
        assertEquals(File(directory, "my test (2)"), file3)

        val fileWithExtension1 = directory.getUniqueFile(fileName = "my test.txt")
        assertEquals(File(directory, "my test.txt"), fileWithExtension1)

        fileWithExtension1.createNewFile()
        val fileWithExtension2 = directory.getUniqueFile(fileName = "my test.txt")
        assertEquals(File(directory, "my test (1).txt"), fileWithExtension2)
    }
}
