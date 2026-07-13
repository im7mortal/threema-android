package ch.threema.base.crypto

import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.emptyByteArray
import ch.threema.common.generateRandomBytes
import ch.threema.libthreema.CryptoException
import java.security.SecureRandom

private val logger = getThreemaLogger("SymmetricEncryptionService")

class SymmetricEncryptionService(
    private val random: SecureRandom,
) {
    fun generateSymmetricKey(): ByteArray =
        random.generateRandomBytes(NaCl.SYMM_KEY_BYTES)

    /**
     * Decrypt a symmetrically encrypted array of bytes using the provided key and nonce.
     * Encryption takes place inplace in order to save memory. Therefore, the original array will
     * be modified and serves as output of the decryption result.
     *
     * @param data input and output data; will be modified
     */
    @Throws(IllegalArgumentException::class, CryptoException::class)
    fun decryptInplace(data: ByteArray, key: ByteArray, nonce: ByteArray) {
        NaCl.symmetricDecryptDataInPlace(data, key, nonce)
    }

    /**
     * Decrypt a symmetrically encrypted array of bytes using the provided key and nonce.
     *
     * @return the decrypted data or `null` if decryption failed
     */
    fun decrypt(data: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray? =
        try {
            NaCl.symmetricDecryptData(data, key, nonce)
        } catch (cryptoException: CryptoException) {
            logger.error("Failed to decrypt data", cryptoException)
            null
        }

    /**
     * Generates a symmetric key and encrypts the data.
     * Encryption is executed inplace to save memory. Therefore, the original data array will be modified.
     *
     * The generated key will be returned with the encryption result.
     *
     * @param data the bytes to encrypt; will be altered during inplace encryption
     * @return The encrypted data alongside the used symmetric encryption key
     */
    fun encryptInplace(data: ByteArray, nonce: ByteArray): SymmetricEncryptionResult {
        val key = generateSymmetricKey()
        return encryptInplace(data, key, nonce)
    }

    /**
     * Encrypts data inplace to save memory. Therefore, the original data array will be modified.
     *
     *
     * The generated key will be returned with the encryption result.
     *
     * @param data the bytes to encrypt; will be altered during inplace encryption
     * @return The encrypted data alongside the used symmetric encryption key. If encryption fails the data is an empty array.
     */
    fun encryptInplace(data: ByteArray, key: ByteArray, nonce: ByteArray): SymmetricEncryptionResult {
        val resultData = try {
            NaCl.symmetricEncryptDataInPlace(data, key, nonce)
            data
        } catch (exception: IllegalArgumentException) {
            logger.error("Failed to encrypt data in-place", exception)
            emptyByteArray()
        } catch (exception: CryptoException) {
            logger.error("Failed to encrypt data in-place", exception)
            emptyByteArray()
        }
        return SymmetricEncryptionResult(resultData, key)
    }

    /**
     * Encrypts file data without altering the original data.
     *
     * @param data the bytes to encrypt
     * @return The encrypted data alongside the used symmetric encryption key. If encryption fails the data is an empty array.
     */
    fun encrypt(data: ByteArray, key: ByteArray, nonce: ByteArray): SymmetricEncryptionResult {
        val encrypted = try {
            NaCl.symmetricEncryptData(data, key, nonce)
        } catch (cryptoException: CryptoException) {
            logger.error("Failed to encrypt data", cryptoException)
            emptyByteArray()
        }
        return SymmetricEncryptionResult(encrypted, key)
    }
}
