package ch.threema.storage.factories

import ch.threema.storage.models.poll.PollVoteModel
import ch.threema.test.TestData
import ch.threema.test.TestDatabaseProvider
import ch.threema.testhelpers.utcDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PollVoteModelFactoryTest {

    @Test
    fun testPollVoteModelFactory() {
        val databaseProvider = TestDatabaseProvider()
        val pollVoteModelFactory = PollVoteModelFactory(databaseProvider)

        // Create 5 entries
        val vote1 = PollVoteModel(
            pollId = 1,
            pollChoiceId = 1001,
            votingIdentity = TestData.Identities.OTHER_1.value,
            selected = true,
            createdAt = utcDate(2026, 1, 1),
            modifiedAt = utcDate(2026, 1, 1),
        )
        pollVoteModelFactory.create(vote1)
        val vote2 = PollVoteModel(
            pollId = 1,
            pollChoiceId = 1002,
            votingIdentity = TestData.Identities.OTHER_1.value,
            selected = false,
            createdAt = utcDate(2026, 1, 1),
            modifiedAt = utcDate(2026, 1, 1),
        )
        pollVoteModelFactory.create(vote2)
        val vote3 = PollVoteModel(
            pollId = 1,
            pollChoiceId = 1003,
            votingIdentity = TestData.Identities.OTHER_2.value,
            selected = true,
            createdAt = utcDate(2026, 1, 1),
            modifiedAt = utcDate(2026, 1, 1),
        )
        pollVoteModelFactory.create(vote3)
        val vote4 = PollVoteModel(
            pollId = 2,
            pollChoiceId = 1004,
            votingIdentity = TestData.Identities.OTHER_1.value,
            selected = true,
            createdAt = utcDate(2026, 1, 1),
            modifiedAt = utcDate(2026, 1, 1),
        )
        pollVoteModelFactory.create(vote4)
        val vote5 = PollVoteModel(
            pollId = 3,
            pollChoiceId = 1004,
            votingIdentity = TestData.Identities.OTHER_1.value,
            selected = true,
            createdAt = utcDate(2026, 1, 1),
            modifiedAt = utcDate(2026, 1, 1),
        )
        pollVoteModelFactory.create(vote5)

        // Verify that ids are set after creation
        assertTrue(vote1.id != 0)
        assertTrue(vote2.id != 0)
        assertTrue(vote3.id != 0)
        assertTrue(vote4.id != 0)
        assertTrue(vote5.id != 0)

        // Verify that votes can be queried by poll id
        assertEquals(
            listOf(vote1, vote2, vote3),
            pollVoteModelFactory.getByPollId(pollId = 1),
        )

        // Verify that votes can be queried by multiple poll ids at once
        assertEquals(
            mapOf(
                1 to listOf(vote1, vote2, vote3),
                2 to listOf(vote4),
            ),
            pollVoteModelFactory.getByPollIds(pollIds = setOf(1, 2)),
        )
    }
}
