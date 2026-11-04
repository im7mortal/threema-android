package ch.threema.android

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UriExtensionsTest {
    @Test
    fun `file uri`() {
        assertTrue(mockk<Uri> { every { scheme } returns "file" }.isFileUri())
        assertTrue(mockk<Uri> { every { scheme } returns "FILE" }.isFileUri())
        assertFalse(mockk<Uri> { every { scheme } returns "content" }.isFileUri())
        assertFalse(mockk<Uri> { every { scheme } returns null }.isFileUri())
    }

    @Test
    fun `content uri`() {
        assertTrue(mockk<Uri> { every { scheme } returns "content" }.isContentUri())
        assertTrue(mockk<Uri> { every { scheme } returns "CONTENT" }.isContentUri())
        assertFalse(mockk<Uri> { every { scheme } returns "file" }.isContentUri())
        assertFalse(mockk<Uri> { every { scheme } returns null }.isContentUri())
    }
}
