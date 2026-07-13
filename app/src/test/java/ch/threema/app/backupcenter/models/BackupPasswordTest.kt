package ch.threema.app.backupcenter.models

import io.mockk.every
import io.mockk.mockk
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class BackupPasswordTest {

    @Test
    fun `cannot create with invalid characters`() {
        arrayOf('?', 'a', '-', 'Ä', ' ', '.').forEach { invalidCharacter ->
            assertFailsWith<IllegalArgumentException> {
                BackupPassword("A".repeat(63) + invalidCharacter)
            }
        }
    }

    @Test
    fun `cannot create with incorrect length`() {
        assertFailsWith<IllegalArgumentException> {
            BackupPassword("A".repeat(63))
        }
        assertFailsWith<IllegalArgumentException> {
            BackupPassword("A".repeat(65))
        }
    }

    @Test
    fun `to chunks`() {
        assertEquals(
            listOf("ABCD", "1234", "ABCD", "1234", "ABCD", "1234", "ABCD", "1234", "ABCD", "1234", "ABCD", "1234", "ABCD", "1234", "ABCD", "1234"),
            BackupPassword("ABCD1234ABCD1234ABCD1234ABCD1234ABCD1234ABCD1234ABCD1234ABCD1234").chunks,
        )
    }

    @Test
    fun `formatted is in chunks`() {
        assertEquals(
            "ABCD 1234 ABCD 1234 ABCD 1234 ABCD 1234 ABCD 1234 ABCD 1234 ABCD 1234 ABCD 1234",
            BackupPassword("ABCD1234ABCD1234ABCD1234ABCD1234ABCD1234ABCD1234ABCD1234ABCD1234").formatted,
        )
    }

    @Test
    fun `toString does not leak the entire password`() {
        val stringRepresentation = BackupPassword("ABCDEFGHIJKLMNOPRSTUVWXYZ01234567890ABCDEFGHIJKLMNOPRSTUVWXYZ012").toString()
        assertFalse("DEF" in stringRepresentation)
        assertFalse("XYZ" in stringRepresentation)
        assertFalse("123" in stringRepresentation)
    }

    @Test
    fun `generate password`() {
        val random = Random(seed = 123)
        val generated = BackupPassword.generate(
            secureRandom = mockk {
                every { nextInt(36) } answers { random.nextInt(36) }
            },
        )
        assertEquals(
            BackupPassword("QLM9RWCKU0005GAH0OA5O5SZE7U8SHPPFRJ6C6UCM221R4H977B0WU8O3R5XW3P4"),
            generated,
        )
    }
}
