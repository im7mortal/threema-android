package ch.threema.app.services

import android.content.Context
import android.net.ConnectivityManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.domain.protocol.csp.messages.file.FileData
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.MessageContentsType
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageServiceImplTest {
    private lateinit var contextMock: Context
    private lateinit var preferenceServiceMock: PreferenceService
    private lateinit var messageService: MessageServiceImpl

    @BeforeTest
    fun setUp() {
        contextMock = mockk()
        preferenceServiceMock = mockk()
        messageService = MessageServiceImpl(
            contextMock,
            mockk<CacheService> {
                every { contactMessageModelCache } returns emptySet()
                every { groupMessageModelCache } returns emptySet()
                every { distributionListMessageCache } returns emptySet()
            },
            /* databaseService = */
            mockk(),
            /* contactService = */
            mockk(),
            /* fileService = */
            mockk(),
            /* identityStore = */
            mockk(),
            /* symmetricEncryptionService = */
            mockk(),
            preferenceServiceMock,
            /* synchronizedSettingsService = */
            mockk(),
            /* pollService = */
            mockk(),
            /* groupService = */
            mockk(),
            /* apiService = */
            mockk(),
            /* downloadService = */
            mockk(),
            /* getConversationCategoryUseCase = */
            mockk(),
            /* blockedIdentitiesService = */
            mockk(),
            /* multiDeviceManager = */
            mockk(),
            /* editHistoryRepository = */
            mockk(),
            /* emojiReactionsRepository = */
            mockk(),
            /* contactModelRepository = */
            mockk(),
            /* globalEventBuses = */
            mockk(),
        )
    }

    @Test
    fun `should auto-download on metered connection`() {
        every { contextMock.getSystemService(ConnectivityManager::class.java) } returns mockk {
            every { isActiveNetworkMetered } returns true
        }
        every { preferenceServiceMock.getMobileAutoDownload() } returns setOf(PreferenceService.AutoDownloadType.IMAGE)

        // Should auto-download images that are small enough
        assertTrue(
            messageService.shouldAutoDownload(
                mockk {
                    every { type } returns MessageType.FILE
                    every { messageContentsType } returns MessageContentsType.IMAGE
                    every { fileData } returns mockk {
                        every { renderingType } returns FileData.RENDERING_MEDIA
                        every { fileSize } returns 3 * 1024 * 1024
                    }
                },
            ),
        )

        // Should auto-download GIFs that are small enough
        assertTrue(
            messageService.shouldAutoDownload(
                mockk {
                    every { type } returns MessageType.FILE
                    every { messageContentsType } returns MessageContentsType.GIF
                    every { fileData } returns mockk {
                        every { renderingType } returns FileData.RENDERING_MEDIA
                        every { fileSize } returns 3 * 1024 * 1024
                    }
                },
            ),
        )

        // Should not auto-download images that are too large
        assertFalse(
            messageService.shouldAutoDownload(
                mockk {
                    every { type } returns MessageType.FILE
                    every { messageContentsType } returns MessageContentsType.IMAGE
                    every { fileData } returns mockk {
                        every { renderingType } returns FileData.RENDERING_MEDIA
                        every { fileSize } returns 6 * 1024 * 1024
                    }
                },
            ),
        )

        // Should not auto-download images that are sent as files
        assertFalse(
            messageService.shouldAutoDownload(
                mockk {
                    every { type } returns MessageType.FILE
                    every { messageContentsType } returns MessageContentsType.IMAGE
                    every { fileData } returns mockk {
                        every { renderingType } returns FileData.RENDERING_DEFAULT
                        every { fileSize } returns 3 * 1024 * 1024
                    }
                },
            ),
        )

        // Should not auto-download videos
        assertFalse(
            messageService.shouldAutoDownload(
                mockk {
                    every { type } returns MessageType.FILE
                    every { messageContentsType } returns MessageContentsType.VIDEO
                    every { fileData } returns mockk {
                        every { renderingType } returns FileData.RENDERING_MEDIA
                        every { fileSize } returns 3 * 1024 * 1024
                    }
                },
            ),
        )

        // Should not auto-download audio files
        assertFalse(
            messageService.shouldAutoDownload(
                mockk {
                    every { type } returns MessageType.FILE
                    every { messageContentsType } returns MessageContentsType.VOICE_MESSAGE
                    every { fileData } returns mockk {
                        every { renderingType } returns FileData.RENDERING_MEDIA
                        every { fileSize } returns 3 * 1024 * 1024
                    }
                },
            ),
        )
    }

    @Test
    fun `should auto-download on non-metered connection`() {
        every { contextMock.getSystemService(ConnectivityManager::class.java) } returns mockk {
            every { isActiveNetworkMetered } returns false
        }
        every { preferenceServiceMock.getWifiAutoDownload() } returns setOf(
            PreferenceService.AutoDownloadType.VIDEO,
            PreferenceService.AutoDownloadType.FILE,
        )

        // Should auto-download videos that are small enough
        assertTrue(
            messageService.shouldAutoDownload(
                mockk {
                    every { type } returns MessageType.FILE
                    every { messageContentsType } returns MessageContentsType.VIDEO
                    every { fileData } returns mockk {
                        every { renderingType } returns FileData.RENDERING_MEDIA
                        every { fileSize } returns 3 * 1024 * 1024
                    }
                },
            ),
        )

        // Should not auto-download videos that are too large
        assertFalse(
            messageService.shouldAutoDownload(
                mockk {
                    every { type } returns MessageType.FILE
                    every { messageContentsType } returns MessageContentsType.VIDEO
                    every { fileData } returns mockk {
                        every { renderingType } returns FileData.RENDERING_MEDIA
                        every { fileSize } returns 6 * 1024 * 1024
                    }
                },
            ),
        )

        // Should auto-download videos that are sent as files
        assertTrue(
            messageService.shouldAutoDownload(
                mockk {
                    every { type } returns MessageType.FILE
                    every { messageContentsType } returns MessageContentsType.VIDEO
                    every { fileData } returns mockk {
                        every { renderingType } returns FileData.RENDERING_DEFAULT
                        every { fileSize } returns 3 * 1024 * 1024
                    }
                },
            ),
        )

        // Should not auto-download images
        assertFalse(
            messageService.shouldAutoDownload(
                mockk {
                    every { type } returns MessageType.FILE
                    every { messageContentsType } returns MessageContentsType.IMAGE
                    every { fileData } returns mockk {
                        every { renderingType } returns FileData.RENDERING_MEDIA
                        every { fileSize } returns 3 * 1024 * 1024
                    }
                },
            ),
        )

        // Should not auto-download GIFs
        assertFalse(
            messageService.shouldAutoDownload(
                mockk {
                    every { type } returns MessageType.FILE
                    every { messageContentsType } returns MessageContentsType.GIF
                    every { fileData } returns mockk {
                        every { renderingType } returns FileData.RENDERING_MEDIA
                        every { fileSize } returns 3 * 1024 * 1024
                    }
                },
            ),
        )

        // Should not auto-download audio files
        assertFalse(
            messageService.shouldAutoDownload(
                mockk {
                    every { type } returns MessageType.FILE
                    every { messageContentsType } returns MessageContentsType.VOICE_MESSAGE
                    every { fileData } returns mockk {
                        every { renderingType } returns FileData.RENDERING_MEDIA
                        every { fileSize } returns 3 * 1024 * 1024
                    }
                },
            ),
        )

        // Should auto-download files
        assertTrue(
            messageService.shouldAutoDownload(
                mockk {
                    every { type } returns MessageType.FILE
                    every { messageContentsType } returns MessageContentsType.FILE
                    every { fileData } returns mockk {
                        every { renderingType } returns FileData.RENDERING_DEFAULT
                        every { fileSize } returns 3 * 1024 * 1024
                    }
                },
            ),
        )
    }
}
