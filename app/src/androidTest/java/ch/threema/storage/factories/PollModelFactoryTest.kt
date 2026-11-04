package ch.threema.storage.factories

import ch.threema.storage.models.poll.PollModel
import ch.threema.test.TestDatabaseProvider
import ch.threema.testhelpers.utcDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PollModelFactoryTest {

    @Test
    fun testPollModelFactory() {
        val databaseProvider = TestDatabaseProvider()
        val pollModelFactory = PollModelFactory(databaseProvider)

        val pollModel = PollModel(
            id = 0,
            apiPollId = "abc",
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

        // Create a new poll
        assertTrue(pollModelFactory.create(pollModel))
        assertTrue(pollModel.id != 0)

        // Query the poll and verify that it is the same
        val queriedPollModel = pollModelFactory.getById(pollModel.id)
        assertEquals(pollModel, queriedPollModel)

        // Query the poll by id and creator identity
        val queriedByPollIdAndIdentity = pollModelFactory.getByApiPollIdAndIdentity(pollModel.apiPollId, pollModel.creatorIdentity)
        assertEquals(pollModel, queriedByPollIdAndIdentity)

        // Verify that querying a poll that does not exist returns null
        assertNull(pollModelFactory.getById(9999))
        assertNull(pollModelFactory.getByApiPollIdAndIdentity("???", pollModel.creatorIdentity))
        assertNull(pollModelFactory.getByApiPollIdAndIdentity(pollModel.apiPollId, "12345679"))

        // Query the poll's api poll id
        assertEquals(pollModel.apiPollId, pollModelFactory.getApiPollIdByLocalPollId(pollModel.id))

        // Query the poll's local poll id
        assertEquals(pollModel.id, pollModelFactory.getLocalPollIdByApiPollId(pollModel.apiPollId))

        // Update the poll
        pollModel.assessment = PollModel.Assessment.MULTIPLE_CHOICE
        pollModel.lastViewedAt = utcDate(year = 2026, month = 1, dayOfMonth = 2)
        pollModelFactory.createOrUpdate(pollModel)
        val queriedUpdatedPollModel = pollModelFactory.getById(pollModel.id)
        assertEquals(PollModel.Assessment.MULTIPLE_CHOICE, queriedUpdatedPollModel?.assessment)
        assertEquals(utcDate(year = 2026, month = 1, dayOfMonth = 2), queriedUpdatedPollModel?.lastViewedAt)

        // Delete the poll
        pollModelFactory.delete(pollModel)
        assertTrue(pollModel.id != 0)
        assertNull(pollModelFactory.getById(pollModel.id))
    }
}
