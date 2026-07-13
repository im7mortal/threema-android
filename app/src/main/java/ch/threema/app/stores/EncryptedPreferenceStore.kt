package ch.threema.app.stores

import ch.threema.localcrypto.exceptions.MasterKeyLockedException
import java.io.IOException

interface EncryptedPreferenceStore {

    fun remove(key: String)

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if encrypting or writing the value fails
     */
    fun save(key: String, value: String?)

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if encrypting or writing the value fails
     */
    fun save(key: String, value: Map<String, String?>?)

    /**
     * Warning: strings in array must NOT contain ";" characters and must NOT be empty.
     *
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if encrypting or writing the value fails
     */
    fun save(key: String, value: Array<String>?)

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if encrypting or writing the value fails
     */
    fun save(key: String, value: ByteArray?)

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if reading or decrypting the stored value fails
     * @return The stored string, or null if no such value is stored
     */
    fun getString(key: String): String?

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if reading or decrypting the stored value fails
     * @return The stored bytes, or null if no such value is stored
     */
    fun getBytes(key: String): ByteArray?

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if reading or decrypting the stored value fails
     */
    fun getStringArray(key: String): Array<String>?

    /**
     * @throws MasterKeyLockedException if the master key is locked
     * @throws IOException if reading or decrypting the stored value fails
     * @return The stored map, or null if no such value is stored or if the value is not a valid map
     */
    fun getMap(key: String): Map<String, String?>?

    fun containsKey(key: String): Boolean

    fun clear()

    companion object {
        const val PREFS_PRIVATE_KEY = "private_key"
        const val PREFS_MD_PROPERTIES = "md_properties"
    }
}
