package ch.threema.app.files

import ch.threema.common.deleteOrThrow
import ch.threema.testhelpers.createTempDirectory
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppDirectoryProviderTest {

    private lateinit var cacheDirectory: File

    @BeforeTest
    fun setUp() {
        cacheDirectory = createTempDirectory()
    }

    @AfterTest
    fun tearDown() {
        cacheDirectory.deleteRecursively()
    }

    @Test
    fun `share directory is always created if it does not exist`() {
        val appDirectoryProvider = AppDirectoryProvider(
            context = mockk {
                every { filesDir } returns mockk()
                every { cacheDir } returns cacheDirectory
            },
        )

        val shareDirectory = appDirectoryProvider.shareDirectory
        assertTrue(shareDirectory.isDirectory)

        shareDirectory.deleteOrThrow()

        val shareDirectory2 = appDirectoryProvider.shareDirectory
        assertTrue(shareDirectory2.isDirectory)
        assertEquals(shareDirectory, shareDirectory2)
    }
}
