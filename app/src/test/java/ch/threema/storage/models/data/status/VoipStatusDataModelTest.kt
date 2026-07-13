package ch.threema.storage.models.data.status

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class VoipStatusDataModelTest {

    @Test
    fun `serialize rejected voip status message`() {
        val serialized = StatusDataModel.serialize(
            VoipStatusDataModel.createRejected(
                callId = 123,
                reason = 1.toByte(),
            ),
        )
        assertEquals(
            """[1,{"status":3,"callId":123,"reason":1}]""",
            serialized,
        )
    }

    @Test
    fun `serialize finished voip status message`() {
        val serialized = StatusDataModel.serialize(
            VoipStatusDataModel.createFinished(
                callId = 123,
                duration = 2.minutes,
            ),
        )
        assertEquals(
            """[1,{"status":2,"callId":123,"duration":120}]""",
            serialized,
        )
    }

    @Test
    fun `serialize missed voip status message`() {
        val serialized = StatusDataModel.serialize(
            VoipStatusDataModel.createMissed(
                callId = 123,
            ),
        )
        assertEquals(
            """[1,{"status":1,"callId":123}]""",
            serialized,
        )
    }

    @Test
    fun `serialize aborted voip status message`() {
        val serialized = StatusDataModel.serialize(
            VoipStatusDataModel.createAborted(
                callId = 123,
            ),
        )
        assertEquals(
            """[1,{"status":4,"callId":123}]""",
            serialized,
        )
    }

    @Test
    fun `deserialize rejected voip status message`() {
        val statusDataModel = StatusDataModel.deserialize(
            """[1,{"status":3,"callId":123,"reason":1}]""",
        )
        assertEquals(
            VoipStatusDataModel.createRejected(
                callId = 123,
                reason = 1.toByte(),
            ),
            statusDataModel,
        )
    }

    @Test
    fun `deserialize finished voip status message`() {
        val statusDataModel = StatusDataModel.deserialize(
            """[1,{"status":2,"callId":123,"duration":120}]""",
        )
        assertEquals(
            VoipStatusDataModel.createFinished(
                callId = 123,
                duration = 2.minutes,
            ),
            statusDataModel,
        )
    }

    @Test
    fun `deserialize missed voip status message`() {
        val statusDataModel = StatusDataModel.deserialize(
            """[1,{"status":1,"callId":123}]""",
        )
        assertEquals(
            VoipStatusDataModel.createMissed(
                callId = 123,
            ),
            statusDataModel,
        )
    }

    @Test
    fun `deserialize aborted voip status message`() {
        val statusDataModel = StatusDataModel.deserialize(
            """[1,{"status":4,"callId":123}]""",
        )
        assertEquals(
            VoipStatusDataModel.createAborted(
                callId = 123,
            ),
            statusDataModel,
        )
    }
}
