package ch.threema.storage.models.data.status

import kotlin.test.Test
import kotlin.test.assertEquals
import testdata.TestData.Identities

class GroupCallStatusDataModelTest {
    @Test
    fun `serialize started group call status message`() {
        val serialized = StatusDataModel.serialize(
            GroupCallStatusDataModel.createStarted(
                callId = "call-id",
                groupId = 42,
                callerIdentity = Identities.OTHER_1.value,
            ),
        )
        assertEquals(
            """[2,{"status":1,"callId":"call-id","callerIdentity":"11111111","groupId":42}]""",
            serialized,
        )
    }

    @Test
    fun `serialize ended group call status message`() {
        val serialized = StatusDataModel.serialize(
            GroupCallStatusDataModel.createEnded(
                callId = "call-id",
            ),
        )
        assertEquals(
            """[2,{"status":2,"callId":"call-id"}]""",
            serialized,
        )
    }

    @Test
    fun `deserialize started group call status message`() {
        val statusDataModel = StatusDataModel.deserialize(
            """[2,{"status":1,"callId":"call-id","callerIdentity":"11111111","groupId":42}]""",
        )
        assertEquals(
            GroupCallStatusDataModel.createStarted(
                callId = "call-id",
                groupId = 42,
                callerIdentity = Identities.OTHER_1.value,
            ),
            statusDataModel,
        )
    }

    @Test
    fun `deserialize ended group call status message`() {
        val statusDataModel = StatusDataModel.deserialize(
            """[2,{"status":2,"callId":"call-id"}]""",
        )
        assertEquals(
            GroupCallStatusDataModel.createEnded(
                callId = "call-id",
            ),
            statusDataModel,
        )
    }
}
