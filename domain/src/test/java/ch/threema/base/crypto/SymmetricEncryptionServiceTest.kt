package ch.threema.base.crypto

import io.mockk.every
import io.mockk.mockk
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SymmetricEncryptionServiceTest {
    @Test
    fun `generate key`() {
        val symmetricEncryptionService = SymmetricEncryptionService(mockSecureRandom())
        val symmetricKey = symmetricEncryptionService.generateSymmetricKey()
        assertContentEquals(MY_KEY, symmetricKey)
    }

    @Test
    fun encrypt() {
        val symmetricEncryptionService = SymmetricEncryptionService(mockk())

        val data = "Hello world".toByteArray()
        val result = symmetricEncryptionService.encrypt(
            data = data,
            key = MY_KEY,
            nonce = MY_NONCE,
        )

        assertContentEquals(
            byteArrayOf(
                -40, -29, -45, -63, -57, 42, -22, -63, -125, -41, -88, 71, 108, -74, -51, -18, 108, 53, 13, -102, -3, 62, 108, 32, 21, -53, -110,
            ),
            result.data,
        )
        assertContentEquals(MY_KEY, result.key)
        assertFalse(result.isEmpty)
        assertContentEquals(
            "Hello world".toByteArray(),
            data,
        )
    }

    @Test
    fun `encrypt in place`() {
        val symmetricEncryptionService = SymmetricEncryptionService(mockk())

        val data = ByteArray(20) { it.toByte() }
        val result = symmetricEncryptionService.encryptInplace(
            data = data,
            key = MY_KEY,
            nonce = MY_NONCE,
        )

        val expectedEncrypted = byteArrayOf(
            -24, 14, -18, -91, 30, -65, 123, 117, -65, -48, 59, -113, -5, -33, 56, 125, 52, 65, 115, -27,
        )
        assertContentEquals(
            expectedEncrypted,
            result.data,
        )
        assertContentEquals(MY_KEY, result.key)
        assertFalse(result.isEmpty)
        assertContentEquals(
            expectedEncrypted,
            data,
        )
    }

    @Test
    fun `encrypt in place with key generation`() {
        val symmetricEncryptionService = SymmetricEncryptionService(mockSecureRandom())

        val data = ByteArray(20) { it.toByte() }
        val result = symmetricEncryptionService.encryptInplace(
            data = data,
            nonce = MY_NONCE,
        )

        val expectedEncrypted = byteArrayOf(
            -24, 14, -18, -91, 30, -65, 123, 117, -65, -48, 59, -113, -5, -33, 56, 125, 52, 65, 115, -27,
        )
        assertContentEquals(
            expectedEncrypted,
            result.data,
        )
        assertContentEquals(MY_KEY, result.key)
        assertFalse(result.isEmpty)
        assertContentEquals(
            expectedEncrypted,
            data,
        )
    }

    @Test
    fun `encrypt in place fails`() {
        val symmetricEncryptionService = SymmetricEncryptionService(mockk())

        val data = byteArrayOf(1, 2, 3) // too short
        val result = symmetricEncryptionService.encryptInplace(
            data = data,
            key = MY_KEY,
            nonce = MY_NONCE,
        )

        val expectedEncrypted = byteArrayOf(
            -24, 14, -18, -91, 30, -65, 123, 117, -65, -48, 59, -113, -5, -33, 56, 125, 52, 65, 115, -27,
        )
        assertTrue(result.data.isEmpty())
        assertContentEquals(MY_KEY, result.key)
        assertTrue(result.isEmpty)
    }

    @Test
    fun decrypt() {
        val symmetricEncryptionService = SymmetricEncryptionService(mockk())

        val data = byteArrayOf(
            -40, -29, -45, -63, -57, 42, -22, -63, -125, -41, -88, 71, 108, -74, -51, -18, 108, 53, 13, -102, -3, 62, 108, 32, 21, -53, -110,
        )
        val originalData = data.copyOf()
        val decryptedData = symmetricEncryptionService.decrypt(
            data = data,
            key = MY_KEY,
            nonce = MY_NONCE,
        )

        assertContentEquals(
            "Hello world".toByteArray(),
            decryptedData,
        )
        assertContentEquals(originalData, data)
    }

    @Test
    fun `decrypt in place`() {
        val symmetricEncryptionService = SymmetricEncryptionService(mockk())

        val data = byteArrayOf(
            -40, -29, -45, -63, -57, 42, -22, -63, -125, -41, -88, 71, 108, -74, -51, -18, 108, 53, 13, -102, -3, 62, 108, 32, 21, -53, -110,
        )
        symmetricEncryptionService.decryptInplace(
            data = data,
            key = MY_KEY,
            nonce = MY_NONCE,
        )

        val expectedPadding = ByteArray(16)
        val expectedDecrypted = "Hello world".toByteArray() + expectedPadding
        assertContentEquals(
            expectedDecrypted,
            data,
        )
    }

    @Test
    fun `decrypt returns null if it fails`() {
        val symmetricEncryptionService = SymmetricEncryptionService(mockk())

        val data = byteArrayOf(
            -40, -29, -45, -63, -57, 42, -22, -63, -125, -41, -88, 71, 108, -74, -51, -18, 108, 53, 13, -102, -3, 62, 108, 32, 21, -53, -110,
        )
        val decryptedData = symmetricEncryptionService.decrypt(
            data = data,
            key = "not a valid key".toByteArray(),
            nonce = MY_NONCE,
        )

        assertNull(decryptedData)
    }

    companion object {
        private val MY_KEY = ByteArray(NaCl.SYMM_KEY_BYTES) { (it + 3).toByte() }
        private val MY_NONCE = ByteArray(NaCl.NONCE_BYTES) { (it + 7).toByte() }

        private fun mockSecureRandom() = mockk<SecureRandom> {
            every { nextBytes(match { it.size == NaCl.SYMM_KEY_BYTES }) } answers { MY_KEY.copyInto(firstArg()) }
        }
    }
}
