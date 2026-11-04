package ch.threema.app.preference.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreferenceServiceTest {
    @Test
    fun `serialize lock mechanism`() {
        assertEquals("none", PreferenceService.LockMechanism.NONE.serialized)
        assertEquals("pin", PreferenceService.LockMechanism.PIN.serialized)
        assertEquals("system", PreferenceService.LockMechanism.SYSTEM.serialized)
        assertEquals("biometric", PreferenceService.LockMechanism.BIOMETRIC.serialized)
    }

    @Test
    fun `deserialize lock mechanism`() {
        assertEquals(
            PreferenceService.LockMechanism.NONE,
            PreferenceService.LockMechanism.deserialize("none"),
        )
        assertEquals(
            PreferenceService.LockMechanism.PIN,
            PreferenceService.LockMechanism.deserialize("pin"),
        )
        assertEquals(
            PreferenceService.LockMechanism.SYSTEM,
            PreferenceService.LockMechanism.deserialize("system"),
        )
        assertEquals(
            PreferenceService.LockMechanism.BIOMETRIC,
            PreferenceService.LockMechanism.deserialize("biometric"),
        )
        assertNull(PreferenceService.LockMechanism.deserialize("unknown"))
    }

    @Test
    fun `serialize theme`() {
        assertEquals("0", PreferenceService.Theme.LIGHT.serialized)
        assertEquals("1", PreferenceService.Theme.DARK.serialized)
        assertEquals("2", PreferenceService.Theme.FOLLOW_SYSTEM.serialized)
    }

    @Test
    fun `deserialize theme`() {
        assertEquals(
            PreferenceService.Theme.LIGHT,
            PreferenceService.Theme.deserialize("0"),
        )
        assertEquals(
            PreferenceService.Theme.DARK,
            PreferenceService.Theme.deserialize("1"),
        )
        assertEquals(
            PreferenceService.Theme.FOLLOW_SYSTEM,
            PreferenceService.Theme.deserialize("2"),
        )
        assertNull(PreferenceService.Theme.deserialize("unknown"))
    }

    @Test
    fun `serialize auto download type`() {
        assertEquals("1", PreferenceService.AutoDownloadType.IMAGE.serialized)
        assertEquals("3", PreferenceService.AutoDownloadType.VOICE_MESSAGE.serialized)
        assertEquals("2", PreferenceService.AutoDownloadType.VIDEO.serialized)
        assertEquals("8", PreferenceService.AutoDownloadType.FILE.serialized)
    }

    @Test
    fun `deserialize auto download type`() {
        assertEquals(
            PreferenceService.AutoDownloadType.IMAGE,
            PreferenceService.AutoDownloadType.deserialize("1"),
        )
        assertEquals(
            PreferenceService.AutoDownloadType.VOICE_MESSAGE,
            PreferenceService.AutoDownloadType.deserialize("3"),
        )
        assertEquals(
            PreferenceService.AutoDownloadType.VIDEO,
            PreferenceService.AutoDownloadType.deserialize("2"),
        )
        assertEquals(
            PreferenceService.AutoDownloadType.FILE,
            PreferenceService.AutoDownloadType.deserialize("8"),
        )
        assertNull(PreferenceService.AutoDownloadType.deserialize("unknown"))
    }
}
