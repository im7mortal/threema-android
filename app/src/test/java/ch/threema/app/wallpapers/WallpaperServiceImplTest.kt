package ch.threema.app.wallpapers

import android.content.Context
import android.net.Uri
import ch.threema.app.BuildFlavor
import ch.threema.app.files.WallpaperFileHandleProvider
import ch.threema.common.emptyByteArray
import ch.threema.common.files.FileHandle
import ch.threema.data.datatypes.ConversationId
import ch.threema.testhelpers.testDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class WallpaperServiceImplTest {

    private lateinit var appContextMock: Context
    private lateinit var wallpaperFileHandleProviderMock: WallpaperFileHandleProvider

    @BeforeTest
    fun setUp() {
        appContextMock = mockk()
        wallpaperFileHandleProviderMock = mockk()
    }

    @Test
    fun `get conversation wallpaper`() {
        val wallpaperService = WallpaperServiceImpl(
            appContext = appContextMock,
            wallpaperFileHandleProvider = wallpaperFileHandleProviderMock,
            dispatcherProvider = mockk(),
        )

        // default wallpaper is used
        every { wallpaperFileHandleProviderMock.get(conversationIdMock) } returns mockk {
            every { exists() } returns false
        }
        assertEquals(
            WallpaperService.Wallpaper.Default,
            wallpaperService.getConversationWallpaper(conversationIdMock),
        )

        // custom wallpaper is used
        every { wallpaperFileHandleProviderMock.get(conversationIdMock) } returns mockk {
            every { exists() } returns true
            every { isEmpty() } returns false
        }
        assertEquals(
            WallpaperService.Wallpaper.Custom(),
            wallpaperService.getConversationWallpaper(conversationIdMock),
        )

        // empty wallpaper is used
        every { wallpaperFileHandleProviderMock.get(conversationIdMock) } returns mockk {
            every { exists() } returns true
            every { isEmpty() } returns true
        }
        assertEquals(
            WallpaperService.Wallpaper.Empty,
            wallpaperService.getConversationWallpaper(conversationIdMock),
        )
    }

    @Test
    fun `get global wallpaper`() {
        val wallpaperService = WallpaperServiceImpl(
            appContext = appContextMock,
            wallpaperFileHandleProvider = wallpaperFileHandleProviderMock,
            dispatcherProvider = mockk(),
        )

        // default wallpaper is used
        every { wallpaperFileHandleProviderMock.getGlobal() } returns mockk {
            every { exists() } returns false
        }
        assertEquals(
            if (BuildFlavor.IS_WORK) {
                // Default global wallpaper is not supported in work builds
                WallpaperService.Wallpaper.Empty
            } else {
                WallpaperService.Wallpaper.Default
            },
            wallpaperService.getGlobalWallpaper(),
        )

        // custom wallpaper is used
        every { wallpaperFileHandleProviderMock.getGlobal() } returns mockk {
            every { exists() } returns true
            every { isEmpty() } returns false
        }
        assertEquals(
            WallpaperService.Wallpaper.Custom(),
            wallpaperService.getGlobalWallpaper(),
        )

        // empty wallpaper is used
        every { wallpaperFileHandleProviderMock.getGlobal() } returns mockk {
            every { exists() } returns true
            every { isEmpty() } returns true
        }
        assertEquals(
            WallpaperService.Wallpaper.Empty,
            wallpaperService.getGlobalWallpaper(),
        )
    }

    @Test
    fun `set conversation wallpaper to custom`() = runTest {
        val wallpaperService = WallpaperServiceImpl(
            appContext = appContextMock,
            wallpaperFileHandleProvider = wallpaperFileHandleProviderMock,
            dispatcherProvider = testDispatcherProvider(),
        )
        val fileUriMock = mockk<Uri>()
        val fileHandleMock = mockk<FileHandle>(relaxed = true)
        every { appContextMock.contentResolver } returns mockk {
            every { openInputStream(fileUriMock) } returns emptyByteArray().inputStream()
        }
        every { wallpaperFileHandleProviderMock.get(conversationIdMock) } returns fileHandleMock

        wallpaperService.setConversationWallpaper(conversationIdMock, WallpaperService.Wallpaper.Custom(fileUriMock))

        verifySequence {
            fileHandleMock.delete()
            fileHandleMock.write()
        }
    }

    @Test
    fun `set conversation wallpaper to empty`() = runTest {
        val wallpaperService = WallpaperServiceImpl(
            appContext = appContextMock,
            wallpaperFileHandleProvider = wallpaperFileHandleProviderMock,
            dispatcherProvider = testDispatcherProvider(),
        )
        val fileHandleMock = mockk<FileHandle>(relaxed = true)
        every { wallpaperFileHandleProviderMock.get(conversationIdMock) } returns fileHandleMock

        wallpaperService.setConversationWallpaper(conversationIdMock, WallpaperService.Wallpaper.Empty)

        verifySequence {
            fileHandleMock.delete()
            fileHandleMock.create()
        }
        verify(exactly = 0) { fileHandleMock.write() }
    }

    @Test
    fun `set conversation wallpaper to default`() = runTest {
        val wallpaperService = WallpaperServiceImpl(
            appContext = appContextMock,
            wallpaperFileHandleProvider = wallpaperFileHandleProviderMock,
            dispatcherProvider = testDispatcherProvider(),
        )
        val fileHandleMock = mockk<FileHandle>(relaxed = true)
        every { wallpaperFileHandleProviderMock.get(conversationIdMock) } returns fileHandleMock

        wallpaperService.setConversationWallpaper(conversationIdMock, WallpaperService.Wallpaper.Default)

        verifySequence {
            fileHandleMock.delete()
        }
        verify(exactly = 0) { fileHandleMock.create() }
        verify(exactly = 0) { fileHandleMock.write() }
    }

    companion object {
        private val conversationIdMock = mockk<ConversationId>()
    }
}
