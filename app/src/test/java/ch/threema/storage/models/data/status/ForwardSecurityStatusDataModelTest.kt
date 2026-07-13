package ch.threema.storage.models.data.status

import ch.threema.storage.models.data.status.ForwardSecurityStatusDataModel.ForwardSecurityStatusType
import kotlin.test.Test
import kotlin.test.assertEquals

class ForwardSecurityStatusDataModelTest {
    @Test
    fun `serialize static text FS status message`() {
        val serialized = StatusDataModel.serialize(
            ForwardSecurityStatusDataModel.create(
                statusType = ForwardSecurityStatusType.STATIC_TEXT,
                staticText = "Hello world",
            ),
        )
        assertEquals(
            """[3,{"status":0,"quantity":0,"staticText":"Hello world"}]""",
            serialized,
        )
    }

    @Test
    fun `serialize messages skipped FS status message`() {
        val serialized = StatusDataModel.serialize(
            ForwardSecurityStatusDataModel.create(
                statusType = ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGES_SKIPPED,
                quantity = 3,
            ),
        )
        assertEquals(
            """[3,{"status":5,"quantity":3,"staticText":null}]""",
            serialized,
        )
    }

    @Test
    fun `deserialize static text FS status message`() {
        val statusDataModel = StatusDataModel.deserialize(
            """[3,{"status":0,"quantity":0,"staticText":"Hello world"}]""",
        )
        assertEquals(
            ForwardSecurityStatusDataModel.create(
                statusType = ForwardSecurityStatusType.STATIC_TEXT,
                staticText = "Hello world",
            ),
            statusDataModel,
        )
    }

    @Test
    fun `deserialize messages skipped FS status message`() {
        val statusDataModel = StatusDataModel.deserialize(
            """[3,{"status":5,"quantity":3,"staticText":null}]""",
        )
        assertEquals(
            ForwardSecurityStatusDataModel.create(
                statusType = ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGES_SKIPPED,
                quantity = 3,
            ),
            statusDataModel,
        )
    }

    @Test
    fun `deserialize messages skipped FS status message with default quantity`() {
        val statusDataModel = StatusDataModel.deserialize(
            """[3,{"status":5}]""",
        )
        assertEquals(
            ForwardSecurityStatusDataModel.create(
                statusType = ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGES_SKIPPED,
                quantity = 0,
            ),
            statusDataModel,
        )
    }
}
