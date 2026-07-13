package ch.threema.app.stores

import ch.threema.common.stateFlowOf
import ch.threema.localcrypto.MasterKey
import ch.threema.localcrypto.MasterKeyImpl
import ch.threema.localcrypto.MasterKeyProvider
import ch.threema.localcrypto.exceptions.MasterKeyLockedException
import ch.threema.testhelpers.createTempDirectory
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow

class EncryptedPreferenceStoreImplTest {

    private lateinit var directory: File
    private lateinit var store: EncryptedPreferenceStore

    @BeforeTest
    fun setUp() {
        directory = createTempDirectory()
        store = EncryptedPreferenceStoreImpl(
            directory = directory,
            masterKeyProvider = MasterKeyProvider(
                masterKeyFlow = stateFlowOf(MasterKeyImpl(MASTER_KEY_DATA)),
            ),
        )
        store.clear()
    }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun checkingForAndRemovingKeys() {
        assertFalse(store.containsKey("foo"))

        store.save("foo", "Hello Wörld")

        assertTrue(store.containsKey("foo"))

        store.remove("foo")

        assertFalse(store.containsKey("foo"))
    }

    @Test
    fun clearDeletesEverything() {
        assertFalse(store.containsKey("foo"))

        store.save("foo", "Hello Wörld")
        store.save("bar", arrayOf("a", "b", "c"))

        store.clear()

        assertFalse(store.containsKey("foo"))
        assertFalse(store.containsKey("bar"))
    }

    @Test
    fun saveAndGetString() {
        assertNull(store.getString("foo"))

        store.save("foo", "Hello Wörld")

        assertEquals("Hello Wörld", store.getString("foo"))
    }

    @Test
    fun saveAndGetNullString() {
        store.save("foo", "Hello Wörld")

        store.save("foo", null as String?)

        assertNull(store.getString("foo"))
    }

    @Test
    fun saveAndGetByteArray() {
        val bytes = byteArrayOf(1, 2, 3)

        store.save("foo", bytes)

        assertContentEquals(bytes, store.getBytes("foo"))
    }

    @Test
    fun saveAndGetStringArray() {
        val strings = arrayOf("Hello", "World")

        store.save("foo", strings)

        assertContentEquals(strings, store.getStringArray("foo"))
    }

    @Test
    fun saveAndGetEmptyStringArray() {
        val strings = arrayOf<String>()

        store.save("foo", strings)

        assertContentEquals(strings, store.getStringArray("foo"))
    }

    @Test
    fun saveAndGetMap() {
        val map = mapOf("a" to "Hello", "b" to "World", "c" to null)

        store.save("foo", map)

        assertEquals(map, store.getMap("foo"))
    }

    @Test
    fun saveAndGetInvalidMap() {
        store.save("foo", "not a map")

        assertNull(store.getMap("foo"))
    }

    @Test
    fun defaultValues() {
        assertNull(store.getString("foo"))
        assertNull(store.getStringArray("foo"))
        assertNull(store.getMap("foo"))
        assertNull(store.getBytes("foo"))
    }

    @Test
    fun getStringFromPreviouslyEncryptedFile() {
        File(directory, ".crs-test")
            .writeBytes(
                byteArrayOf(
                    -116, 41, -38, -100, 96, 67, -28, -11, -118, -59, -33, -25,
                    58, -51, 27, -9, -84, -102, -29, 97, 97, 101, -124, 32, 111,
                    57, -54, -68, 37, 100, 119, 42,
                ),
            )

        assertEquals("Hello", store.getString("test"))
    }

    @Test
    fun storingNewValueReplacesThePrevious() {
        store.save("foo", "Hello Wörld! This is a long string, much longer than the second one.")
        store.save("foo", "Hi")

        assertEquals("Hi", store.getString("foo"))
    }

    @Test
    fun stringArrayValuesCannotContainSemicolon() {
        assertFailsWith<IllegalArgumentException> {
            store.save("foo", arrayOf("Hi", "Hello;World"))
        }
    }

    @Test
    fun readFailsWhenMasterKeyIsLocked() {
        val masterKeyFlow = MutableStateFlow<MasterKey?>(MasterKeyImpl(MASTER_KEY_DATA))
        store = EncryptedPreferenceStoreImpl(
            directory = directory,
            masterKeyProvider = MasterKeyProvider(
                masterKeyFlow = masterKeyFlow,
            ),
        )
        store.clear()
        store.save("foo", "Test")

        masterKeyFlow.value = null

        assertFailsWith<MasterKeyLockedException> {
            store.getString("foo")
        }
    }

    @Test
    fun saveFailsWhenMasterKeyIsLocked() {
        store = EncryptedPreferenceStoreImpl(
            directory = directory,
            masterKeyProvider = MasterKeyProvider(
                masterKeyFlow = stateFlowOf(null),
            ),
        )
        store.clear()

        assertFailsWith<MasterKeyLockedException> {
            store.save("foo", "Hello")
        }
    }

    companion object {
        private val MASTER_KEY_DATA = ByteArray(32) { it.toByte() }
    }
}
