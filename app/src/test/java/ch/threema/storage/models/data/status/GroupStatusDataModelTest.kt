package ch.threema.storage.models.data.status

import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import testdata.TestData.Identities

class GroupStatusDataModelTest {
    @Test
    fun `serialize created group status message`() {
        val serialized = StatusDataModel.serialize(
            GroupStatusDataModel.create(
                type = GroupStatusType.CREATED,
            ),
        )
        assertEquals(
            """[4,{"status":0}]""",
            serialized,
        )
    }

    @Test
    fun `serialize renamed group status message`() {
        val serialized = StatusDataModel.serialize(
            GroupStatusDataModel.create(
                type = GroupStatusType.RENAMED,
                newGroupName = "New!",
            ),
        )
        assertEquals(
            """[4,{"status":1,"newGroupName":"New!"}]""",
            serialized,
        )
    }

    @Test
    fun `serialize member added group status message`() {
        val serialized = StatusDataModel.serialize(
            GroupStatusDataModel.create(
                type = GroupStatusType.MEMBER_ADDED,
                newGroupName = "New!",
                identity = Identities.OTHER_1.value,
            ),
        )
        assertEquals(
            """[4,{"status":3,"identity":"11111111"}]""",
            serialized,
        )
    }

    @Test
    fun `serialize member voted group status message`() {
        val serialized = StatusDataModel.serialize(
            GroupStatusDataModel.create(
                type = GroupStatusType.MODIFIED_VOTE,
                identity = Identities.OTHER_1.value,
                pollName = "Poll!",
            ),
        )
        assertEquals(
            """[4,{"status":9,"identity":"11111111","ballotName":"Poll!"}]""",
            serialized,
        )
    }

    @Test
    fun `deserialize created group status message`() {
        val statusDataModel = StatusDataModel.deserialize(
            """[4,{"status":0}]""",
        )
        assertEquals(
            GroupStatusDataModel.create(
                type = GroupStatusType.CREATED,
            ),
            statusDataModel,
        )
    }

    @Test
    fun `deserialize renamed group status message`() {
        val statusDataModel = StatusDataModel.deserialize(
            """[4,{"status":1,"newGroupName":"New!"}]""",
        )
        assertEquals(
            GroupStatusDataModel.create(
                type = GroupStatusType.RENAMED,
                newGroupName = "New!",
            ),
            statusDataModel,
        )
    }

    @Test
    fun `deserialize member added group status message`() {
        val statusDataModel = StatusDataModel.deserialize(
            """[4,{"status":3,"identity":"11111111"}]""",
        )
        assertEquals(
            GroupStatusDataModel.create(
                type = GroupStatusType.MEMBER_ADDED,
                newGroupName = "New!",
                identity = Identities.OTHER_1.value,
            ),
            statusDataModel,
        )
    }

    @Test
    fun `deserialize member voted group status message`() {
        val statusDataModel = StatusDataModel.deserialize(
            """[4,{"status":9,"identity":"11111111","ballotName":"Poll!"}]""",
        )
        assertEquals(
            GroupStatusDataModel.create(
                type = GroupStatusType.MODIFIED_VOTE,
                identity = Identities.OTHER_1.value,
                pollName = "Poll!",
            ),
            statusDataModel,
        )
    }

    @Test
    fun `deserialize invalid group status messages`() {
        // 'status' is missing
        assertNull(
            StatusDataModel.deserialize(
                """[4,{}]""",
            ),
        )
        // required 'newGroupName' is missing
        assertNull(
            StatusDataModel.deserialize(
                """[4,{"status":1}]""",
            ),
        )
        // required 'newGroupName' is null
        assertNull(
            StatusDataModel.deserialize(
                """[4,{"status":1,"newGroupName":null}]""",
            ),
        )
        // required 'identity' is missing
        assertNull(
            StatusDataModel.deserialize(
                """[4,{"status":4}]""",
            ),
        )
        // required 'identity' is null
        assertNull(
            StatusDataModel.deserialize(
                """[4,{"status":4,"identity":null}]""",
            ),
        )
        // required 'ballotName' is missing
        assertNull(
            StatusDataModel.deserialize(
                """[4,{"status":11}]""",
            ),
        )
        // required 'ballotName' is null
        assertNull(
            StatusDataModel.deserialize(
                """[4,{"status":11,"ballotName":null}]""",
            ),
        )
        // unknown status
        assertNull(
            StatusDataModel.deserialize(
                """[4,{"status":14}]""",
            ),
        )
    }
}
