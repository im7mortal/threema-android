package ch.threema.app.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileUtilTest {

    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()
    private val packageName
        get() = appContext.packageName

    @Test
    fun testValidPaths() {
        // Arrange
        val paths = listOf(
            "/data/data/other/app/files/.crs-private_key/",
            "/sdcard/Download/some_file.txt",
        )

        paths.forEach { path ->
            // Act
            val isSanePath = FileUtil.isSanePath(appContext, path)

            // Assert
            assertTrue(isSanePath)
        }
    }

    @Test
    fun testValidPathsWithTraversals() {
        // Arrange
        val paths = listOf(
            "/data/data/other/app/../app/./files/.crs-private_key/",
            "../../../../sdcard/Download/some_file.txt",
            "/data/../sdcard/Download/some_file.txt",
        )

        paths.forEach { path ->
            // Act
            val isSanePath = FileUtil.isSanePath(appContext, path)

            // Assert
            assertTrue(isSanePath)
        }
    }

    @Test
    fun testInvalidInternalPaths() {
        // Arrange
        val paths = listOf(
            "/data/data/$packageName/databases/db.db",
            "/data/data/$packageName/files/file.txt",
            "/data/data/$packageName/file.txt",
        )

        paths.forEach { path ->
            // Act
            val isSanePath = FileUtil.isSanePath(appContext, path)

            // Assert
            assertFalse(isSanePath)
        }
    }

    @Test
    fun testInvalidInternalPathsWithPathTraversals() {
        // Arrange
        val paths = listOf(
            "../.././data/data/$packageName/databases/db.db",
            "/data/data/../data/$packageName/files/file.txt",
            "/data/data/$packageName/../$packageName/../$packageName/file.txt",
            "/data/data/.///./$packageName/file.txt",
            "/data/../../../data/data/$packageName/file.txt",
        )

        paths.forEach { path ->
            // Act
            val isSanePath = FileUtil.isSanePath(appContext, path)

            // Assert
            assertFalse(isSanePath)
        }
    }
}
