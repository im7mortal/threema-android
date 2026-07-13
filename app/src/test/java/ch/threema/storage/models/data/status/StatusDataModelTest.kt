package ch.threema.storage.models.data.status

import kotlin.test.Test
import kotlin.test.assertNull

class StatusDataModelTest {
    @Test
    fun `deserialize invalid status model`() {
        // invalid json
        assertIsInvalidStatusDataModel("")
        // not a JSON array
        assertIsInvalidStatusDataModel("{}")
        // missing params object
        assertIsInvalidStatusDataModel("""[1]""")
        // unknown type
        assertIsInvalidStatusDataModel("""[5,{}]""")
    }

    private fun assertIsInvalidStatusDataModel(input: String) {
        assertNull(
            StatusDataModel.deserialize(input),
        )
    }
}
