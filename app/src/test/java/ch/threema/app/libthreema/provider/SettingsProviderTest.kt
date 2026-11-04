package ch.threema.app.libthreema.provider

import android.content.Context
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.preference.service.SynchronizedSettingsServiceImpl
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ExcludedSyncIdentitiesService
import ch.threema.app.test.InMemoryPreferenceStore
import ch.threema.domain.types.IdentityString
import ch.threema.libthreema.GroupCallPolicy
import ch.threema.libthreema.O2oCallConnectionPolicy
import ch.threema.libthreema.O2oCallPolicy
import ch.threema.libthreema.O2oCallVideoPolicy
import ch.threema.libthreema.ReadReceiptPolicy
import ch.threema.libthreema.ScreenshotPolicy
import ch.threema.libthreema.Settings
import ch.threema.libthreema.SettingsUpdate
import ch.threema.libthreema.TypingIndicatorPolicy
import ch.threema.libthreema.UnknownContactPolicy
import ch.threema.test.TestIdentityProvider
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import testdata.TestData

class SettingsProviderTest {
    private val guaranteedIdentityProvider = GuaranteedIdentityProvider(
        identityProvider = TestIdentityProvider(
            identity = TestData.Identities.ME,
        ),
    )

    @Test
    fun `app settings are written and read correctly`() {
        // Arrange
        val synchronizedSettingsServiceMock = mockSynchronizedSettingsService()
        val settingsProvider = createSettingsProvider(
            synchronizedSettingsService = synchronizedSettingsServiceMock,
            blockedIdentitiesService = mockBlockedIdentitiesService(),
            excludedSyncIdentitiesService = mockExcludedSyncIdentitiesService(),
            guaranteedIdentityProvider = guaranteedIdentityProvider,
        )
        val appSettings = Settings(
            unknownContactPolicy = UnknownContactPolicy.ALLOW_UNKNOWN,
            readReceiptPolicy = ReadReceiptPolicy.SEND_READ_RECEIPT,
            typingIndicatorPolicy = TypingIndicatorPolicy.SEND_TYPING_INDICATOR,
            o2oCallPolicy = O2oCallPolicy.ALLOW_O2O_CALL,
            o2oCallConnectionPolicy = O2oCallConnectionPolicy.ALLOW_DIRECT_CONNECTION,
            o2oCallVideoPolicy = O2oCallVideoPolicy.ALLOW_VIDEO,
            groupCallPolicy = GroupCallPolicy.ALLOW_GROUP_CALL,
            screenshotPolicy = ScreenshotPolicy.ALLOW_SCREENSHOT,
            blockedIdentities = listOf(TestData.Identities.OTHER_1.value),
            excludeFromSyncIdentities = listOf(TestData.Identities.OTHER_1.value, TestData.Identities.OTHER_2.value),
        )
        val appSettingsUpdate = SettingsUpdate(
            unknownContactPolicy = UnknownContactPolicy.ALLOW_UNKNOWN,
            readReceiptPolicy = ReadReceiptPolicy.SEND_READ_RECEIPT,
            typingIndicatorPolicy = TypingIndicatorPolicy.SEND_TYPING_INDICATOR,
            o2oCallPolicy = O2oCallPolicy.ALLOW_O2O_CALL,
            o2oCallConnectionPolicy = O2oCallConnectionPolicy.ALLOW_DIRECT_CONNECTION,
            o2oCallVideoPolicy = O2oCallVideoPolicy.ALLOW_VIDEO,
            groupCallPolicy = GroupCallPolicy.ALLOW_GROUP_CALL,
            screenshotPolicy = ScreenshotPolicy.ALLOW_SCREENSHOT,
            blockedIdentities = setOf(TestData.Identities.OTHER_1.value),
            excludeFromSyncIdentities = setOf(TestData.Identities.OTHER_1.value, TestData.Identities.OTHER_2.value),
        )

        // Act
        settingsProvider.update(appSettingsUpdate)
        val actualAppPolicies = settingsProvider.get()

        // Assert
        assertEquals(
            expected = appSettings,
            actual = actualAppPolicies,
        )
    }

    @Test
    fun `inverse app policies are written and read correctly`() {
        // Arrange
        val synchronizedSettingsServiceMock = mockSynchronizedSettingsService()
        val settingsProvider = createSettingsProvider(
            synchronizedSettingsService = synchronizedSettingsServiceMock,
            blockedIdentitiesService = mockBlockedIdentitiesService(),
            excludedSyncIdentitiesService = mockExcludedSyncIdentitiesService(),
            guaranteedIdentityProvider = guaranteedIdentityProvider,
        )
        val appSettings = Settings(
            unknownContactPolicy = UnknownContactPolicy.BLOCK_UNKNOWN,
            readReceiptPolicy = ReadReceiptPolicy.DONT_SEND_READ_RECEIPT,
            typingIndicatorPolicy = TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR,
            o2oCallPolicy = O2oCallPolicy.DENY_O2O_CALL,
            o2oCallConnectionPolicy = O2oCallConnectionPolicy.REQUIRE_RELAYED_CONNECTION,
            o2oCallVideoPolicy = O2oCallVideoPolicy.DENY_VIDEO,
            groupCallPolicy = GroupCallPolicy.DENY_GROUP_CALL,
            screenshotPolicy = ScreenshotPolicy.DENY_SCREENSHOT,
            blockedIdentities = emptyList(),
            excludeFromSyncIdentities = emptyList(),
        )
        val appSettingsUpdate = SettingsUpdate(
            unknownContactPolicy = UnknownContactPolicy.BLOCK_UNKNOWN,
            readReceiptPolicy = ReadReceiptPolicy.DONT_SEND_READ_RECEIPT,
            typingIndicatorPolicy = TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR,
            o2oCallPolicy = O2oCallPolicy.DENY_O2O_CALL,
            o2oCallConnectionPolicy = O2oCallConnectionPolicy.REQUIRE_RELAYED_CONNECTION,
            o2oCallVideoPolicy = O2oCallVideoPolicy.DENY_VIDEO,
            groupCallPolicy = GroupCallPolicy.DENY_GROUP_CALL,
            screenshotPolicy = ScreenshotPolicy.DENY_SCREENSHOT,
            blockedIdentities = emptySet(),
            excludeFromSyncIdentities = emptySet(),
        )

        // Act
        settingsProvider.update(appSettingsUpdate)
        val actualAppPolicies = settingsProvider.get()

        // Assert
        assertEquals(
            expected = appSettings,
            actual = actualAppPolicies,
        )
    }

    private fun createSettingsProvider(
        synchronizedSettingsService: SynchronizedSettingsService = mockk(),
        blockedIdentitiesService: BlockedIdentitiesService = mockk(),
        excludedSyncIdentitiesService: ExcludedSyncIdentitiesService = mockk(),
        guaranteedIdentityProvider: GuaranteedIdentityProvider = mockk(),
    ) = SettingsProvider(
        synchronizedSettingsService = synchronizedSettingsService,
        blockedIdentitiesService = blockedIdentitiesService,
        excludedSyncIdentitiesService = excludedSyncIdentitiesService,
        guaranteedIdentityProvider = guaranteedIdentityProvider,
    )

    private fun mockSynchronizedSettingsService(): SynchronizedSettingsService {
        val inMemoryPreferenceStore = InMemoryPreferenceStore()
        val contextMock = mockk<Context> {
            // We just use the resource id as value. This is only needed for mapping the settings to a fixed key. As long as it does not change while
            // running a test, we are safe.
            every { getString(any()) } answers { firstArg<Int>().toString() }
        }
        return SynchronizedSettingsServiceImpl(
            appContext = contextMock,
            preferenceStore = inMemoryPreferenceStore,
            taskManager = mockk(),
            multiDeviceManager = mockk(),
        )
    }

    private fun mockBlockedIdentitiesService(): BlockedIdentitiesService = mockk {
        val blockedIdentities = mutableSetOf<IdentityString>()
        every { getAllBlockedIdentities() } returns blockedIdentities
        every { replaceBlockedIdentitiesWithoutReflection(any()) } answers {
            blockedIdentities.clear()
            blockedIdentities.addAll(firstArg())
        }
    }

    private fun mockExcludedSyncIdentitiesService(): ExcludedSyncIdentitiesService = mockk {
        val excludedSyncIdentities = mutableSetOf<IdentityString>()
        every { getAllExcludedIdentities() } returns excludedSyncIdentities
        every { replaceExcludedIdentitiesWithoutReflection(any()) } answers {
            excludedSyncIdentities.clear()
            excludedSyncIdentities.addAll(firstArg())
        }
    }
}
