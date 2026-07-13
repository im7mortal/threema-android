package ch.threema.storage.models.data.media

import kotlin.test.Test
import kotlin.test.assertEquals

class PollDataModelTest {

    @Test
    fun `serialize poll data model`() {
        assertEquals(
            "[2,123]",
            PollDataModel(
                type = PollDataModel.Type.POLL_MODIFIED,
                pollId = 123,
            )
                .toString(),
        )

        assertEquals(
            "[1,456]",
            PollDataModel(
                type = PollDataModel.Type.POLL_CREATED,
                pollId = 456,
            )
                .toString(),
        )

        assertEquals(
            "[3,789]",
            PollDataModel(
                type = PollDataModel.Type.POLL_CLOSED,
                pollId = 789,
            )
                .toString(),
        )
    }

    @Test
    fun `deserialize poll data model`() {
        assertEquals(
            PollDataModel(
                type = PollDataModel.Type.POLL_MODIFIED,
                pollId = 123,
            ),
            PollDataModel.deserialize("[2,123]"),
        )
    }
}
