package ch.threema.app.stores

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import ch.threema.testhelpers.expectItem
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PreferencesStoreImplTest {

    private val sharedPreferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("preferences-store-test", 0)
    private lateinit var store: PreferenceStore

    @BeforeTest
    fun setUp() {
        store = PreferenceStoreImpl(
            sharedPreferences = sharedPreferences,
            commit = true,
        )
        store.clear()
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
    fun saveAndRestoreString() {
        assertNull(store.getString("foo"))

        store.save("foo", "Hello Wörld")

        assertEquals("Hello Wörld", store.getString("foo"))
    }

    @Test
    fun saveAndRestoreInt() {
        assertEquals(-1, store.getInt("foo", defaultValue = -1))

        store.save("foo", 123)

        assertEquals(123, store.getInt("foo"))
    }

    @Test
    fun saveAndRestoreInstant() {
        assertNull(store.getInstant("foo"))
        assertFalse(store.containsKey("foo"))

        store.save("foo", Instant.ofEpochMilli(1_766_407_316_000L))

        assertEquals(Instant.ofEpochMilli(1_766_407_316_000L), store.getInstant("foo"))

        store.save("foo", null as Instant?)

        assertNull(store.getInstant("foo"))
        assertFalse(store.containsKey("foo"))
    }

    @Test
    fun saveAndRestoreBoolean() {
        assertEquals(false, store.getBoolean("foo"))

        store.save("foo", true)

        assertEquals(true, store.getBoolean("foo"))
    }

    @Test
    fun saveAndRestoreLong() {
        assertEquals(-1L, store.getLong("foo", defaultValue = -1L))

        store.save("foo", 123_000_000_000_000L)

        assertEquals(123_000_000_000_000L, store.getLong("foo"))
    }

    @Test
    fun saveAndRestoreFloat() {
        assertEquals(-1f, store.getFloat("foo", -1f))

        store.save("foo", 123.456f)

        assertEquals(123.456f, store.getFloat("foo", -1f))
    }

    @Test
    fun saveAndRestoreByteArray() {
        val bytes = byteArrayOf(1, 2, 3)

        store.save("foo", bytes)

        assertContentEquals(bytes, store.getBytes("foo"))
    }

    @Test
    fun saveAndRestoreStringArray() {
        val strings = arrayOf("Hello", "World")

        store.save("foo", strings)

        assertContentEquals(strings, store.getStringArray("foo"))
    }

    @Test
    fun saveAndRestoreMap() {
        val map = mapOf("a" to "Hello", "b" to "World", "c" to null)

        store.save("foo", map)

        assertEquals(map, store.getMap("foo"))
    }

    @Test
    fun defaultValues() {
        assertNull(store.getString("foo"))
        assertEquals(0f, store.getFloat("foo"))
        assertEquals(0L, store.getLong("foo"))
        assertEquals(0, store.getInt("foo"))
        assertEquals(false, store.getBoolean("foo"))
        assertNull(store.getStringArray("foo"))
        assertEquals(emptyMap(), store.getMap("foo"))
        assertNull(store.getBytes("foo"))
    }

    @Test
    fun restorePreviouslyStoredValue() {
        sharedPreferences.edit {
            putInt("int", 123)
            putString("string", "Hello")
            putString("map", """[["A","a"],["B","b"],["C"],["D",null]]""")
            putString("string-array", "A;B;C")
            putStringSet("string-set", setOf("A", "B", "C"))
        }

        assertEquals(123, store.getInt("int"))
        assertEquals("Hello", store.getString("string"))
        assertEquals(mapOf("A" to "a", "B" to "b", "C" to null, "D" to null), store.getMap("map"))
        assertContentEquals(arrayOf("A", "B", "C"), store.getStringArray("string-array"))
        assertEquals(setOf("A", "B", "C"), store.getStringSet("string-set"))
    }

    @Test
    fun stringArrayValuesCannotContainSemicolon() {
        assertFailsWith<IllegalArgumentException> {
            store.save("foo", arrayOf("Hi", "Hello;World"))
        }
    }

    @Test
    fun watchBooleanShouldEmitCorrectValueChangesToKey() = runTest {
        val key = "is_colored"
        store.watchBoolean(key, false).test {
            // Expect the defined default value (as the key does not exist on disk right now)
            expectItem(false)

            // Change the value
            store.save(key, true)
            expectItem(true)

            // Should emit the defined default value when removing the preference
            store.remove(key)
            expectItem(false)

            // Add the key again
            store.save(key, true)
            expectItem(true)

            // Expect no distinct change
            store.save(key, true)
            expectNoEvents()

            // Change the value (to the default value)
            store.save(key, false)
            expectItem(false)

            // Expecting no distinct change, as the last saved value was already the default value
            store.remove(key)
            expectNoEvents()

            // Adding the key again (with its default value)
            store.save(key, false)
            expectNoEvents()

            // Changing the value
            store.save(key, true)
            expectItem(true)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun watchLongShouldEmitCorrectValueChangesToKey() = runTest {
        val key = "number_of_things"
        store.watchLong(key, -1L).test {
            // Expect the defined default value (as the key does not exist on disk right now)
            expectItem(-1L)

            // Change the value
            store.save(key, 1L)
            expectItem(1L)

            // Should emit the defined default value when removing the preference
            store.remove(key)
            expectItem(-1L)

            // Add the key again
            store.save(key, 2L)
            expectItem(2L)

            // Expect no distinct change
            store.save(key, 2L)
            expectNoEvents()

            // Change the value (to the default value)
            store.save(key, -1L)
            expectItem(-1L)

            // Expecting no distinct change, as the last saved value was already the default value
            store.remove(key)
            expectNoEvents()

            // Adding the key again (with its default value)
            store.save(key, -1L)
            expectNoEvents()

            // Changing the value
            store.save(key, 3L)
            expectItem(3L)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun watchIntShouldEmitCorrectValueChangesToKey() = runTest {
        val key = "number_of_things"
        store.watchInt(key, -1).test {
            // Expect the defined default value (as the key does not exist on disk right now)
            expectItem(-1)

            // Change the value
            store.save(key, 1)
            expectItem(1)

            // Should emit the defined default value when removing the preference
            store.remove(key)
            expectItem(-1)

            // Add the key again
            store.save(key, 2)
            expectItem(2)

            // Expect no distinct change
            store.save(key, 2)
            expectNoEvents()

            // Change the value (to the default value)
            store.save(key, -1)
            expectItem(-1)

            // Expecting no distinct change, as the last saved value was already the default value
            store.remove(key)
            expectNoEvents()

            // Adding the key again (with its default value)
            store.save(key, -1)
            expectNoEvents()

            // Changing the value
            store.save(key, 3)
            expectItem(3)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun watchInstantShouldEmitCorrectValueChangesToKey() = runTest {
        val key = "some_time"
        store.watchInstant(key).test {
            // Expect null initially (as the key does not exist on disk right now)
            expectItem(null)

            // Change the value
            store.save(key, Instant.ofEpochMilli(123))
            expectItem(Instant.ofEpochMilli(123))

            // Should emit the defined default value when removing the preference
            store.remove(key)
            expectItem(null)

            // Add the key again
            store.save(key, Instant.ofEpochMilli(456))
            expectItem(Instant.ofEpochMilli(456))

            // Expect no distinct change
            store.save(key, Instant.ofEpochMilli(456))
            expectNoEvents()

            // Change the value (to the default value)
            store.save(key, null as Instant?)
            expectItem(null)

            // Expecting no distinct change, as the last saved value was already null
            store.remove(key)
            expectNoEvents()

            // Adding the key again (with null as the value)
            store.save(key, null as Instant?)
            expectNoEvents()

            // Changing the value
            store.save(key, Instant.ofEpochMilli(789))
            expectItem(Instant.ofEpochMilli(789))

            cancelAndIgnoreRemainingEvents()
        }
    }
}
