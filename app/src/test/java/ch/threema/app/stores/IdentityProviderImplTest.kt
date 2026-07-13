package ch.threema.app.stores

import ch.threema.domain.types.Identity
import ch.threema.localcrypto.MasterKeyStorageManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IdentityProviderImplTest {
    @Test
    fun `get non-existing identity`() {
        val preferenceStoreMock = mockk<PreferenceStore> {
            every { getString("identity") } returns null
        }
        val masterKeyStorageManagerMock = mockk<MasterKeyStorageManager> {
            every { keyExists() } returns true
        }
        val identityProvider = IdentityProviderImpl(preferenceStoreMock, masterKeyStorageManagerMock)

        assertNull(identityProvider.getIdentity())
    }

    @Test
    fun `get existing identity`() {
        val preferenceStoreMock = mockk<PreferenceStore> {
            every { getString("identity") } returns "01234567"
        }
        val masterKeyStorageManagerMock = mockk<MasterKeyStorageManager> {
            every { keyExists() } returns true
        }
        val identityProvider = IdentityProviderImpl(preferenceStoreMock, masterKeyStorageManagerMock)

        assertEquals(Identity("01234567"), identityProvider.getIdentity())
    }

    @Test
    fun `get existing identity, but master key does not exist`() {
        val preferenceStoreMock = mockk<PreferenceStore> {
            every { getString("identity") } returns "01234567"
        }
        val masterKeyStorageManagerMock = mockk<MasterKeyStorageManager> {
            every { keyExists() } returns false
        }
        val identityProvider = IdentityProviderImpl(preferenceStoreMock, masterKeyStorageManagerMock)

        assertNull(identityProvider.getIdentity())
    }

    @Test
    fun `get invalid identity`() {
        val preferenceStoreMock = mockk<PreferenceStore> {
            every { getString("identity") } returns "invalid"
        }
        val masterKeyStorageManagerMock = mockk<MasterKeyStorageManager> {
            every { keyExists() } returns true
        }
        val identityProvider = IdentityProviderImpl(preferenceStoreMock, masterKeyStorageManagerMock)

        assertNull(identityProvider.getIdentity())
    }

    @Test
    fun `set identity`() {
        val preferenceStoreMock = mockk<PreferenceStore>(relaxed = true) {
            every { getString("identity") } returns null
        }
        val masterKeyStorageManagerMock = mockk<MasterKeyStorageManager> {
            every { keyExists() } returns true
        }
        val identityProvider = IdentityProviderImpl(preferenceStoreMock, masterKeyStorageManagerMock)

        identityProvider.setIdentity(Identity("01234567"))

        verify { preferenceStoreMock.save("identity", "01234567") }
        assertEquals(Identity("01234567"), identityProvider.getIdentity())
    }
}
