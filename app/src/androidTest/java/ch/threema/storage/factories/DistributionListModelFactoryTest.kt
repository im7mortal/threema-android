package ch.threema.storage.factories

import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.storage.models.DistributionListModel
import ch.threema.test.TestDatabaseProvider
import ch.threema.testhelpers.utcDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DistributionListModelFactoryTest {

    private lateinit var distributionListModelFactory: DistributionListModelFactory

    @BeforeTest
    fun beforeTest() {
        distributionListModelFactory = DistributionListModelFactory(TestDatabaseProvider())
    }

    @Test
    fun allExist_empty_input() {
        // act
        val allExists = distributionListModelFactory.allExist(emptySet<Long>())

        // assert
        assertTrue(allExists)
    }

    @Test
    fun allExist_empty_db() {
        // act
        val allExists = distributionListModelFactory.allExist(setOf(1L, 2L))

        // assert
        assertFalse(allExists)
    }

    @Test
    fun allExist_partially() {
        // arrange
        distributionListModelFactory.create(createDistributionListModel(1L))
        distributionListModelFactory.create(createDistributionListModel(2L))

        // act
        val allExists = distributionListModelFactory.allExist(setOf(1L, 2L, 3L))

        // assert
        assertFalse(allExists)
    }

    @Test
    fun allExist_all() {
        // arrange
        distributionListModelFactory.create(createDistributionListModel(1L))
        distributionListModelFactory.create(createDistributionListModel(2L))
        distributionListModelFactory.create(createDistributionListModel(3L))
        distributionListModelFactory.create(createDistributionListModel(4L))

        // act
        val allExists = distributionListModelFactory.allExist(setOf(1L, 2L, 3L))

        // assert
        assertTrue(allExists)
    }

    private companion object {

        /**
         * @param id use value of `0` to auto-generate by a wicked algorithm in [DistributionListModelFactory.getUniqueId]
         */
        fun createDistributionListModel(
            id: Long = 0L,
        ) = DistributionListModel().apply {
            this.id = id
            this.createdAt = utcDate(2026, 7, 28)
            this.lastUpdate = utcDate(2026, 7, 29)
            this.isAdHocDistributionList = false
            this.conversationCategory = ConversationCategory.DEFAULT
            this.conversationVisibility = ConversationVisibility.NORMAL
            this.name = "Distribution list"
        }
    }
}
