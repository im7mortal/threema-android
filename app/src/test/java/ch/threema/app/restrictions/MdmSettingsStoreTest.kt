package ch.threema.app.restrictions

import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.domain.protocol.api.work.WorkMDMSettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class MdmSettingsStoreTest {

    @Test
    fun `get mdm settings`() {
        val store = MdmSettingsStore(
            encryptedPreferenceStore = mockk {
                every { containsKey("wrk_app_restriction") } returns true
                every { getString("wrk_app_restriction") } returns
                    """{"override":true,"parameters":{"int":42,"float":3.5,"boolean":true,"string":"String","int_as_string":"123"}}"""
            },
        )

        assertEquals(
            WorkMDMSettings(
                override = true,
                parameters = mapOf(
                    "int" to 42,
                    "float" to 3.5f,
                    "boolean" to true,
                    "string" to "String",
                    "int_as_string" to "123",
                ),
            ),
            store.getWorkMDMSettings(),
        )
    }

    @Test
    fun `store mdm settings`() {
        val encryptedPreferenceStoreMock = mockk<EncryptedPreferenceStore>(relaxed = true)
        val store = MdmSettingsStore(
            encryptedPreferenceStore = encryptedPreferenceStoreMock,
        )

        store.storeWorkMDMSettings(
            WorkMDMSettings(
                override = true,
                parameters = mapOf(
                    "int" to 42,
                    "float" to 3.5,
                    "boolean" to true,
                    "string" to "String",
                    "int_as_string" to "123",
                ),
            ),
        )

        verify(exactly = 1) {
            encryptedPreferenceStoreMock.save(
                "wrk_app_restriction",
                """{"override":true,"parameters":{"int":42,"float":3.5,"boolean":true,"string":"String","int_as_string":"123"}}""",
            )
        }
    }
}
