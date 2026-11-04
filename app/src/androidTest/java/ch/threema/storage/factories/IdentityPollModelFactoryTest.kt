package ch.threema.storage.factories

import ch.threema.storage.models.poll.IdentityPollModel
import ch.threema.storage.models.poll.PollModel
import ch.threema.test.TestData
import ch.threema.test.TestDatabaseProvider
import ch.threema.testhelpers.utcDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdentityPollModelFactoryTest {

    @Test
    fun testIdentityPollModelFactory() {
        val databaseProvider = TestDatabaseProvider()
        val identityPollModelFactory = IdentityPollModelFactory(databaseProvider)
        val pollModel = PollModel(
            id = 1,
            apiPollId = "ff000000000000aa",
            creatorIdentity = "12345678",
            name = "Poll",
            state = PollModel.State.OPEN,
            assessment = PollModel.Assessment.SINGLE_CHOICE,
            type = PollModel.Type.INTERMEDIATE,
            choiceType = PollModel.ChoiceType.TEXT,
            displayType = PollModel.DisplayType.SUMMARY_MODE,
            createdAt = utcDate(year = 2026, month = 1, dayOfMonth = 1),
            modifiedAt = utcDate(year = 2026, month = 1, dayOfMonth = 1),
            lastViewedAt = null,
        )
        PollModelFactory(databaseProvider).create(pollModel)

        // Create 3 entries
        val identityPollModel1 = IdentityPollModel(
            identity = TestData.Identities.OTHER_1.value,
            pollId = 1,
        )
        identityPollModelFactory.create(identityPollModel1)
        val identityPollModel2 = IdentityPollModel(
            identity = TestData.Identities.OTHER_2.value,
            pollId = 2,
        )
        identityPollModelFactory.create(identityPollModel2)
        val identityPollModel3 = IdentityPollModel(
            identity = TestData.Identities.OTHER_3.value,
            pollId = 3,
        )
        identityPollModelFactory.create(identityPollModel3)

        // Verify that ids are set after creation
        assertTrue(identityPollModel1.id != 0)
        assertTrue(identityPollModel2.id != 0)
        assertTrue(identityPollModel3.id != 0)

        // Verify that we can test for existence by api poll id and identity
        assertTrue(
            identityPollModelFactory.exists(
                apiPollId = "ff000000000000aa",
                identity = TestData.Identities.OTHER_1.value,
            ),
        )
        assertFalse(
            identityPollModelFactory.exists(
                apiPollId = "ff000000000000aa",
                identity = TestData.Identities.OTHER_2.value,
            ),
        )

        // Verify that we can query by poll id
        assertEquals(
            identityPollModel3,
            identityPollModelFactory.getByPollId(pollId = 3),
        )

        // Verify that querying by a poll id that does not exist returns null
        assertNull(identityPollModelFactory.getByPollId(pollId = 4))

        // Verify that we can query by an identity and a poll id at the same time
        assertEquals(
            identityPollModel3,
            identityPollModelFactory.getByIdentityAndPollId(identity = TestData.Identities.OTHER_3.value, pollId = 3),
        )

        // Verify that querying by an identity-poll-id-combo that doesn't exist returns null
        assertNull(
            identityPollModelFactory.getByIdentityAndPollId(
                identity = TestData.Identities.OTHER_3.value,
                pollId = 4,
            ),
        )

        // Verify that we can query by multiple poll ids at once
        assertEquals(
            mapOf(
                1 to TestData.Identities.OTHER_1.value,
                2 to TestData.Identities.OTHER_2.value,
            ),
            identityPollModelFactory.getIdentitiesByPollIds(setOf(1, 2)),
        )

        // Verify that an entry can no longer be queried after being deleted
        identityPollModelFactory.deleteByPollId(pollId = 3)
        assertNull(identityPollModelFactory.getByPollId(pollId = 3))
    }
}
