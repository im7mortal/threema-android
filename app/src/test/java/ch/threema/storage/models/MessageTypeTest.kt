package ch.threema.storage.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageTypeTest {

    @Test
    fun `serialize message types`() {
        assertEquals(0, MessageType.TEXT.serializedValue)
        assertEquals(4, MessageType.LOCATION.serializedValue)
        assertEquals(6, MessageType.STATUS.serializedValue)
        assertEquals(7, MessageType.POLL.serializedValue)
        assertEquals(8, MessageType.FILE.serializedValue)
        assertEquals(9, MessageType.VOIP_STATUS.serializedValue)
        assertEquals(10, MessageType.VIRTUAL.serializedValue)
        assertEquals(11, MessageType.GROUP_CALL_STATUS.serializedValue)
        assertEquals(12, MessageType.FORWARD_SECURITY_STATUS.serializedValue)
        assertEquals(13, MessageType.GROUP_STATUS.serializedValue)
    }

    @Test
    fun `deserialize message types`() {
        assertEquals(MessageType.TEXT, MessageType.deserialize(0))
        assertEquals(MessageType.LOCATION, MessageType.deserialize(4))
        assertEquals(MessageType.STATUS, MessageType.deserialize(6))
        assertEquals(MessageType.POLL, MessageType.deserialize(7))
        assertEquals(MessageType.FILE, MessageType.deserialize(8))
        assertEquals(MessageType.VOIP_STATUS, MessageType.deserialize(9))
        assertEquals(MessageType.VIRTUAL, MessageType.deserialize(10))
        assertEquals(MessageType.GROUP_CALL_STATUS, MessageType.deserialize(11))
        assertEquals(MessageType.FORWARD_SECURITY_STATUS, MessageType.deserialize(12))
        assertEquals(MessageType.GROUP_STATUS, MessageType.deserialize(13))

        // Former types that don't exist anymore
        assertNull(MessageType.deserialize(1)) // IMAGE
        assertNull(MessageType.deserialize(2)) // VIDEO
        assertNull(MessageType.deserialize(3)) // VOICEMESSAGE
        assertNull(MessageType.deserialize(5)) // CONTACT

        // Types that don't exist (yet)
        assertNull(MessageType.deserialize(14))
    }

    @Test
    fun `status messages`() {
        assertTrue(MessageType.STATUS.isStatus)
        assertTrue(MessageType.VOIP_STATUS.isStatus)
        assertTrue(MessageType.VIRTUAL.isStatus)
        assertTrue(MessageType.GROUP_CALL_STATUS.isStatus)
        assertTrue(MessageType.FORWARD_SECURITY_STATUS.isStatus)
        assertTrue(MessageType.GROUP_STATUS.isStatus)

        assertFalse(MessageType.TEXT.isStatus)
        assertFalse(MessageType.FILE.isStatus)
        assertFalse(MessageType.LOCATION.isStatus)
        assertFalse(MessageType.POLL.isStatus)
    }

    @Test
    fun `only text and file message types can be edited`() {
        assertTrue(MessageType.TEXT.canBeEdited)
        assertTrue(MessageType.FILE.canBeEdited)

        assertFalse(MessageType.LOCATION.canBeEdited)
        assertFalse(MessageType.STATUS.canBeEdited)
        assertFalse(MessageType.POLL.canBeEdited)
        assertFalse(MessageType.VOIP_STATUS.canBeEdited)
        assertFalse(MessageType.VIRTUAL.canBeEdited)
        assertFalse(MessageType.GROUP_CALL_STATUS.canBeEdited)
        assertFalse(MessageType.FORWARD_SECURITY_STATUS.canBeEdited)
        assertFalse(MessageType.GROUP_STATUS.canBeEdited)
    }

    @Test
    fun `only text, location, poll, and file message types can be emoji reacted`() {
        assertTrue(MessageType.TEXT.supportsEmojiReactions)
        assertTrue(MessageType.LOCATION.supportsEmojiReactions)
        assertTrue(MessageType.POLL.supportsEmojiReactions)
        assertTrue(MessageType.FILE.supportsEmojiReactions)

        assertFalse(MessageType.STATUS.supportsEmojiReactions)
        assertFalse(MessageType.VOIP_STATUS.supportsEmojiReactions)
        assertFalse(MessageType.VIRTUAL.supportsEmojiReactions)
        assertFalse(MessageType.GROUP_CALL_STATUS.supportsEmojiReactions)
        assertFalse(MessageType.FORWARD_SECURITY_STATUS.supportsEmojiReactions)
        assertFalse(MessageType.GROUP_STATUS.supportsEmojiReactions)
    }

    @Test
    fun `all non-status message types require a message id`() {
        assertTrue(MessageType.TEXT.requiresMessageId)
        assertTrue(MessageType.FILE.requiresMessageId)
        assertTrue(MessageType.LOCATION.requiresMessageId)
        assertTrue(MessageType.POLL.requiresMessageId)

        assertFalse(MessageType.STATUS.requiresMessageId)
        assertFalse(MessageType.VOIP_STATUS.requiresMessageId)
        assertFalse(MessageType.VIRTUAL.requiresMessageId)
        assertFalse(MessageType.GROUP_CALL_STATUS.requiresMessageId)
        assertFalse(MessageType.FORWARD_SECURITY_STATUS.requiresMessageId)
        assertFalse(MessageType.GROUP_STATUS.requiresMessageId)
    }
}
